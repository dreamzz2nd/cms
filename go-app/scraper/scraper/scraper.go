package scraper

import (
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"nyamimo-go/client"
)

type ScraperEngine struct {
	client  *http.Client
	baseURL string
}

func NewScraperEngine() *ScraperEngine {
	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	return &ScraperEngine{
		baseURL: "https://otakudesu.blog",
		client: &http.Client{
			Transport: tr,
			Timeout:   15 * time.Second,
		},
	}
}

// Fetch raw HTML with realistic browser headers
func (e *ScraperEngine) fetchHTML(targetURL string) (string, error) {
	req, err := http.NewRequest("GET", targetURL, nil)
	if err != nil {
		return "", err
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
	req.Header.Set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
	req.Header.Set("Referer", "https://google.com/")

	resp, err := e.client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	return string(body), nil
}

// StartJob runs scraper asynchronously in background
func (e *ScraperEngine) StartJob(mode string) error {
	store := GetStore()
	store.mu.Lock()
	if store.IsRunning {
		store.mu.Unlock()
		return fmt.Errorf("bot scraper sedang berjalan")
	}
	store.IsRunning = true
	store.stopChan = make(chan struct{})
	store.mu.Unlock()

	go func() {
		defer func() {
			store.mu.Lock()
			store.IsRunning = false
			store.CurrentTask = ""
			store.mu.Unlock()
			_ = store.FlushToDisk()
			store.AddLog("INFO", "🏁 Background scraper task selesai.")
		}()

		switch mode {
		case "ongoing":
			store.mu.Lock()
			store.CurrentTask = "Scraping Anime Ongoing"
			store.mu.Unlock()
			_, _ = e.ScrapeOngoing()
		case "completed":
			store.mu.Lock()
			store.CurrentTask = "Scraping Anime Tamat"
			store.mu.Unlock()
			_, _ = e.ScrapeCompleted()
		case "all":
			fallthrough
		case "full":
			store.mu.Lock()
			store.CurrentTask = "Scraping Seluruh Katalog Anime (A - Z)"
			store.mu.Unlock()
			_ = e.ScrapeFullCatalogAZ()
		case "quick":
			fallthrough
		default:
			store.mu.Lock()
			store.CurrentTask = "Scraping Ongoing & Tamat"
			store.mu.Unlock()
			_, _ = e.ScrapeOngoing()
			time.Sleep(300 * time.Millisecond)
			_, _ = e.ScrapeCompleted()
		}
	}()

	return nil
}

// Stop halts any active scraping process
func (e *ScraperEngine) Stop() {
	store := GetStore()
	store.mu.Lock()
	defer store.mu.Unlock()
	if store.IsRunning && store.stopChan != nil {
		select {
		case <-store.stopChan:
		default:
			close(store.stopChan)
		}
		store.IsRunning = false
		store.CurrentTask = "Dihentikan"
		store.AddLog("WARN", "Perintah berhenti diterima dari panel admin.")
	}
}

// Scrape Ongoing Anime with all episodes
func (e *ScraperEngine) ScrapeOngoing() ([]*ScrapedAnime, error) {
	store := GetStore()
	store.AddLog("INFO", "🌐 Menghubungkan ke sumber Otakudesu.blog (Ongoing)...")

	html, err := e.fetchHTML(e.baseURL + "/ongoing-anime/")
	if err != nil {
		html, err = e.fetchHTML(e.baseURL + "/")
		if err != nil {
			store.AddLog("ERROR", "❌ Gagal menghubungi server sumber: "+err.Error())
			return nil, err
		}
	}

	itemRegex := regexp.MustCompile(`(?s)<div class=['"]detpost['"]>.*?<div class=['"]epz['"][^>]*>(?:<[^>]+>)*\s*([^<]+)</div>.*?<div class=['"]thumb['"]>\s*<a\s+href="([^"]+)".*?<img[^>]+src="([^"]+)".*?<h2 class=['"]jdlflm['"]>([^<]+)</h2>`)
	matches := itemRegex.FindAllStringSubmatch(html, -1)

	if len(matches) == 0 {
		itemRegex = regexp.MustCompile(`(?s)<div class=['"]thumb['"]>\s*<a\s+href="([^"]+)".*?<img[^>]+src="([^"]+)".*?<h2 class=['"]jdlflm['"]>([^<]+)</h2>.*?<div class=['"]epz['"][^>]*>(?:<[^>]+>)*\s*([^<]+)</div>`)
		rawMatches := itemRegex.FindAllStringSubmatch(html, -1)
		for _, rm := range rawMatches {
			matches = append(matches, []string{rm[0], rm[4], rm[1], rm[2], rm[3]})
		}
	}

	var results []*ScrapedAnime
	store.AddLog("INFO", fmt.Sprintf("📋 Ditemukan %d anime Ongoing. Memproses detail & seluruh episode...", len(matches)))

	for idx, m := range matches {
		select {
		case <-store.stopChan:
			store.AddLog("WARN", "Scraping Ongoing dihentikan oleh pengguna.")
			return results, nil
		default:
		}

		episode := strings.TrimSpace(m[1])
		rawURL := strings.TrimSpace(m[2])
		imgURL := strings.TrimSpace(m[3])
		title := strings.TrimSpace(m[4])

		slug := extractSlugFromURL(rawURL)
		if slug == "" {
			continue
		}

		store.AddLog("INFO", fmt.Sprintf("[%d/%d] ⏳ Mengikis: %s...", idx+1, len(matches), title))

		// Scrape full detail with ALL episodes
		detail, _ := e.ScrapeAnimeDetail(slug)

		anime := &ScrapedAnime{
			Title:    title,
			Slug:     slug,
			Img:      imgURL,
			Poster:   imgURL,
			Score:    "8.6",
			Status:   "Ongoing",
			Type:     "TV Series",
			Episode:  episode,
			Synopsis: fmt.Sprintf("Nonton streaming anime %s subtitle Indonesia gratis kualitas jernih full HD di Nyamimo.", title),
		}

		if detail != nil && len(detail.Episodes) > 0 {
			anime.Episodes = detail.Episodes
			if detail.Synopsis != "" {
				anime.Synopsis = detail.Synopsis
			}
			if detail.Rating != "" {
				anime.Score = detail.Rating
			}
			if len(detail.Genres) > 0 {
				var gn []string
				for _, g := range detail.Genres {
					gn = append(gn, g.Title)
				}
				anime.GenreNames = gn
			}
		} else {
			anime.Episodes = []client.EpisodeRef{
				{
					Title:     "Episode " + cleanEpisodeNumber(episode),
					DetailEps: slug + "-episode-" + cleanEpisodeNumber(episode),
					Episode:   cleanEpisodeNumber(episode),
				},
			}
		}

		store.SaveAnime(anime, true)
		results = append(results, anime)
		store.AddLog("SUCCESS", fmt.Sprintf("[%d/%d] ✅ Tersimpan: %s (%d Episode)", idx+1, len(matches), title, len(anime.Episodes)))
		time.Sleep(30 * time.Millisecond)
	}

	store.LastScraped = time.Now()
	_ = store.FlushToDisk()
	store.AddLog("SUCCESS", fmt.Sprintf("🎉 Scraping Ongoing selesai! Total %d anime tersimpan di database lokal.", len(results)))
	return results, nil
}

// Scrape Completed Anime with all episodes
func (e *ScraperEngine) ScrapeCompleted() ([]*ScrapedAnime, error) {
	store := GetStore()
	store.AddLog("INFO", "🌐 Menghubungkan ke sumber Otakudesu.blog (Completed/Tamat)...")

	html, err := e.fetchHTML(e.baseURL + "/complete-anime/")
	if err != nil {
		store.AddLog("ERROR", "❌ Gagal menghubungi sumber anime tamat: "+err.Error())
		return nil, err
	}

	itemRegex := regexp.MustCompile(`(?s)<div class=['"]detpost['"]>.*?<div class=['"]epz['"][^>]*>(?:<[^>]+>)*\s*([^<]+)</div>.*?<div class=['"]epztipe['"][^>]*>(?:<[^>]+>)*\s*([^<]+)</div>.*?<div class=['"]thumb['"]>\s*<a\s+href="([^"]+)".*?<img[^>]+src="([^"]+)".*?<h2 class=['"]jdlflm['"]>([^<]+)</h2>`)
	matches := itemRegex.FindAllStringSubmatch(html, -1)

	var results []*ScrapedAnime
	store.AddLog("INFO", fmt.Sprintf("📋 Ditemukan %d anime Tamat. Memproses detail & seluruh episode...", len(matches)))

	for idx, m := range matches {
		select {
		case <-store.stopChan:
			store.AddLog("WARN", "Scraping Tamat dihentikan oleh pengguna.")
			return results, nil
		default:
		}

		episode := strings.TrimSpace(m[1])
		score := strings.TrimSpace(m[2])
		rawURL := strings.TrimSpace(m[3])
		imgURL := strings.TrimSpace(m[4])
		title := strings.TrimSpace(m[5])

		slug := extractSlugFromURL(rawURL)
		if slug == "" {
			continue
		}

		store.AddLog("INFO", fmt.Sprintf("[%d/%d] ⏳ Mengikis: %s...", idx+1, len(matches), title))

		// Scrape full detail with ALL episodes
		detail, _ := e.ScrapeAnimeDetail(slug)

		anime := &ScrapedAnime{
			Title:    title,
			Slug:     slug,
			Img:      imgURL,
			Poster:   imgURL,
			Score:    cleanScore(score),
			Status:   "Completed",
			Type:     "TV Series",
			Episode:  episode,
			Synopsis: fmt.Sprintf("Nonton streaming anime %s Sub Indo full tamat kualitas HD tanpa iklan di Nyamimo.", title),
		}

		if detail != nil && len(detail.Episodes) > 0 {
			anime.Episodes = detail.Episodes
			if detail.Synopsis != "" {
				anime.Synopsis = detail.Synopsis
			}
			if len(detail.Genres) > 0 {
				var gn []string
				for _, g := range detail.Genres {
					gn = append(gn, g.Title)
				}
				anime.GenreNames = gn
			}
		} else {
			anime.Episodes = []client.EpisodeRef{
				{
					Title:     "Full Episode (" + cleanEpisodeNumber(episode) + ")",
					DetailEps: slug + "-complete",
					Episode:   cleanEpisodeNumber(episode),
				},
			}
		}

		store.SaveAnime(anime, false)
		results = append(results, anime)
		store.AddLog("SUCCESS", fmt.Sprintf("[%d/%d] ✅ Tersimpan Tamat: %s (%d Episode)", idx+1, len(matches), title, len(anime.Episodes)))
		time.Sleep(30 * time.Millisecond)
	}

	store.LastScraped = time.Now()
	_ = store.FlushToDisk()
	store.AddLog("SUCCESS", fmt.Sprintf("🎉 Scraping Tamat selesai! Total %d anime tersimpan.", len(results)))
	return results, nil
}

// Scrape Full Anime Catalog A-Z (Crawls all anime & extracts ALL episodes)
func (e *ScraperEngine) ScrapeFullCatalogAZ() error {
	store := GetStore()

	store.AddLog("INFO", "🚀 MEMULAI SCRAPING SEMUA ANIME DARI A - Z...")

	// 1. Ambil Ongoing & Completed terlebih dahulu
	_, _ = e.ScrapeOngoing()
	time.Sleep(300 * time.Millisecond)
	_, _ = e.ScrapeCompleted()
	time.Sleep(300 * time.Millisecond)

	// 2. Ambil List Anime A-Z
	store.AddLog("INFO", "🌐 Menghubungkan ke indeks A-Z Otakudesu.blog...")
	html, err := e.fetchHTML(e.baseURL + "/anime-list/")
	if err != nil {
		store.AddLog("ERROR", "❌ Gagal mengambil daftar anime A-Z: "+err.Error())
		return err
	}

	// Match all anime URLs from the A-Z list
	linkRegex := regexp.MustCompile(`<a\s+[^>]*href=["']https?://otakudesu\.[^/]+/anime/([^/"']+)/?["'][^>]*>(.*?)</a>`)
	matches := linkRegex.FindAllStringSubmatch(html, -1)

	if len(matches) == 0 {
		linkRegex = regexp.MustCompile(`<a\s+[^>]*href=["'](/anime/([^/"']+)/?)["'][^>]*>(.*?)</a>`)
		matches = linkRegex.FindAllStringSubmatch(html, -1)
	}

	store.AddLog("INFO", fmt.Sprintf("🎯 Total %d anime ditemukan di katalog A-Z! Memulai scraping seluruh episode...", len(matches)))

	cleanTagRegex := regexp.MustCompile(`<[^>]+>`)
	savedCount := 0

	for i, m := range matches {
		select {
		case <-store.stopChan:
			store.AddLog("WARN", "Scraping dihentikan oleh pengguna.")
			return nil
		default:
		}

		slug := strings.TrimSpace(m[1])
		rawTitle := strings.TrimSpace(m[2])
		cleanTitle := strings.TrimSpace(cleanTagRegex.ReplaceAllString(rawTitle, ""))
		if idx := strings.Index(cleanTitle, "<color"); idx != -1 {
			cleanTitle = strings.TrimSpace(cleanTitle[:idx])
		}
		if cleanTitle == "" {
			cleanTitle = slug
		}

		// Check if already in store with valid detail & episodes
		if existing, ok := store.Animes[slug]; ok && len(existing.Episodes) > 0 && existing.Img != "" {
			store.AddLog("INFO", fmt.Sprintf("[%d/%d] ⚡ Sudah ada di database: %s (%d Episode)", i+1, len(matches), cleanTitle, len(existing.Episodes)))
			continue
		}

		store.AddLog("INFO", fmt.Sprintf("[%d/%d] ⏳ Mengikis anime: %s...", i+1, len(matches), cleanTitle))

		// Scrape detail with all episodes
		detail, err := e.ScrapeAnimeDetail(slug)
		if err != nil || detail == nil || detail.Title == "" {
			time.Sleep(50 * time.Millisecond)
			continue
		}

		savedCount++
		store.AddLog("SUCCESS", fmt.Sprintf("[%d/%d] ✅ Tersimpan: %s (%d Episode)", i+1, len(matches), detail.Title, len(detail.Episodes)))

		if (i+1)%5 == 0 || i == len(matches)-1 {
			_ = store.FlushToDisk()
		}

		time.Sleep(80 * time.Millisecond)
	}

	store.LastScraped = time.Now()
	_ = store.FlushToDisk()
	store.AddLog("SUCCESS", fmt.Sprintf("🎉 SELESAI! Database Nyamimo sekarang memiliki %d anime lengkap dengan semua episode!", len(store.Animes)))
	return nil
}

// Scrape Detail Anime & Extract ALL Episodes
func (e *ScraperEngine) ScrapeAnimeDetail(slug string) (*client.AnimeDetailData, error) {
	store := GetStore()

	// Check local cache first
	if existing, ok := store.GetAnime(slug); ok && len(existing.Episodes) > 1 && existing.Img != "" {
		return existing, nil
	}

	targetURL := fmt.Sprintf("%s/anime/%s/", e.baseURL, slug)
	html, err := e.fetchHTML(targetURL)
	if err != nil {
		return nil, err
	}

	titleRegex := regexp.MustCompile(`(?s)<div class="infozingle">.*?<span><b>Judul</b>:\s*([^<]+)</span>`)
	statusRegex := regexp.MustCompile(`<span><b>Status</b>:\s*([^<]+)</span>`)
	scoreRegex := regexp.MustCompile(`<span><b>Skor</b>:\s*([^<]+)</span>`)
	imgRegex := regexp.MustCompile(`(?s)<div\s+class=['"]fotoanime['"][^>]*>.*?<img[^>]+src=['"]([^'"]+)['"]`)
	synRegex := regexp.MustCompile(`(?s)<div class=['"]sinopc['"][^>]*>.*?<p>(.*?)</p>`)

	titleMatch := titleRegex.FindStringSubmatch(html)
	statusMatch := statusRegex.FindStringSubmatch(html)
	scoreMatch := scoreRegex.FindStringSubmatch(html)
	imgMatch := imgRegex.FindStringSubmatch(html)
	synMatch := synRegex.FindStringSubmatch(html)

	title := slug
	if len(titleMatch) > 1 {
		title = strings.TrimSpace(titleMatch[1])
	}
	status := "Completed"
	if len(statusMatch) > 1 {
		status = strings.TrimSpace(statusMatch[1])
	}
	score := "8.5"
	if len(scoreMatch) > 1 {
		score = strings.TrimSpace(scoreMatch[1])
	}
	img := ""
	if len(imgMatch) > 1 {
		img = strings.TrimSpace(imgMatch[1])
	}
	synopsis := fmt.Sprintf("Nonton streaming anime %s subtitle Indonesia gratis kualitas HD di Nyamimo.", title)
	if len(synMatch) > 1 {
		synopsis = strings.TrimSpace(regexp.MustCompile(`<[^>]+>`).ReplaceAllString(synMatch[1], ""))
	}

	// Extract ALL Episodes from episode list (supports otakudesu episode pattern)
	epRegex := regexp.MustCompile(`<a\s+href=["'](https?://otakudesu\.[^/]+/episode/([^/"']+)/?)["'][^>]*>(.*?)</a>`)
	epMatches := epRegex.FindAllStringSubmatch(html, -1)

	if len(epMatches) == 0 {
		epRegex = regexp.MustCompile(`(?s)<li><span><a\s+href=["']([^"']+)["'][^>]*>(.*?)</a></span>`)
		epMatches = epRegex.FindAllStringSubmatch(html, -1)
	}

	var episodes []client.EpisodeRef
	for _, em := range epMatches {
		epURL := strings.TrimSpace(em[1])
		epTitle := strings.TrimSpace(regexp.MustCompile(`<[^>]+>`).ReplaceAllString(em[len(em)-1], ""))
		epSlug := extractSlugFromURL(epURL)

		if epSlug != "" {
			epepNum := cleanEpisodeNumber(epTitle)
			if epepNum == "" {
				epepNum = fmt.Sprintf("%d", len(episodes)+1)
			}
			episodes = append(episodes, client.EpisodeRef{
				Title:     epTitle,
				DetailEps: epSlug,
				Episode:   epepNum,
			})
		}
	}

	// Extract Genres
	var genreNames []string
	genreRegex := regexp.MustCompile(`<a\s+href=["']https?://otakudesu\.[^/]+/genres/([^/"']+)/?["'][^>]*>(.*?)</a>`)
	genreMatches := genreRegex.FindAllStringSubmatch(html, -1)
	for _, gm := range genreMatches {
		genreNames = append(genreNames, strings.TrimSpace(gm[2]))
	}

	anime := &ScrapedAnime{
		Title:      title,
		Slug:       slug,
		Img:        img,
		Poster:     img,
		Score:      score,
		Synopsis:   synopsis,
		Status:     status,
		Type:       "TV Series",
		Episode:    fmt.Sprintf("%d Episode", len(episodes)),
		GenreNames: genreNames,
		Episodes:   episodes,
	}

	store.SaveAnime(anime, strings.EqualFold(status, "Ongoing"))
	detail := anime.ToAnimeDetailData()
	return &detail, nil
}

// ScrapeEpisodeDetail extracts playable video streams (DesuStream, Blogger, VidHide, etc.) and download links
func (e *ScraperEngine) ScrapeEpisodeDetail(slug string) (*client.EpisodeDetailResponse, error) {
	store := GetStore()

	// Check local cache first
	if cached, ok := store.GetEpisode(slug); ok && (len(cached.Videos) > 0 || cached.VideoURL != "") {
		return cached, nil
	}

	targetURL := fmt.Sprintf("%s/episode/%s/", e.baseURL, slug)
	html, err := e.fetchHTML(targetURL)
	if err != nil {
		return nil, err
	}

	// 1. Extract Title & Episode Info
	titleRegex := regexp.MustCompile(`<h1[^>]*class="posttl"[^>]*>([^<]+)</h1>`)
	titleMatch := titleRegex.FindStringSubmatch(html)
	epTitle := slug
	if len(titleMatch) > 1 {
		epTitle = strings.TrimSpace(titleMatch[1])
	}
	epNum := cleanEpisodeNumber(epTitle)

	// 2. Extract Action Nonce & Embed Action from script
	actionRegex := regexp.MustCompile(`action:\s*["']([a-f0-9]{32})["']`)
	actionMatches := actionRegex.FindAllStringSubmatch(html, -1)

	var nonceAction, embedAction string
	if len(actionMatches) >= 2 {
		embedAction = actionMatches[0][1]
		nonceAction = actionMatches[1][1]
	}

	// 3. Extract Mirror Stream links with data-content
	mirrorRegex := regexp.MustCompile(`(?s)<li[^>]*>\s*<a\s+[^>]*data-content="([^"]+)"[^>]*>([^<]+)</a>`)
	mirrorMatches := mirrorRegex.FindAllStringSubmatch(html, -1)

	var videos []client.PlayerOption
	var defaultVideoURL string

	// Fetch Nonce from admin-ajax
	var nonce string
	if nonceAction != "" {
		form := url.Values{}
		form.Set("action", nonceAction)
		ajaxReq, _ := http.NewRequest("POST", e.baseURL+"/wp-admin/admin-ajax.php", strings.NewReader(form.Encode()))
		ajaxReq.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		ajaxReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
		ajaxReq.Header.Set("Referer", targetURL)
		ajaxReq.Header.Set("X-Requested-With", "XMLHttpRequest")

		if ajaxResp, err := e.client.Do(ajaxReq); err == nil {
			ajaxBody, _ := io.ReadAll(ajaxResp.Body)
			ajaxResp.Body.Close()
			var nonceRes struct {
				Data string `json:"data"`
			}
			_ = json.Unmarshal(ajaxBody, &nonceRes)
			nonce = nonceRes.Data
		}
	}

	// If nonce failed with action 2, try action 1
	if nonce == "" && embedAction != "" {
		form := url.Values{}
		form.Set("action", embedAction)
		ajaxReq, _ := http.NewRequest("POST", e.baseURL+"/wp-admin/admin-ajax.php", strings.NewReader(form.Encode()))
		ajaxReq.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		ajaxReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
		ajaxReq.Header.Set("Referer", targetURL)
		ajaxReq.Header.Set("X-Requested-With", "XMLHttpRequest")

		if ajaxResp, err := e.client.Do(ajaxReq); err == nil {
			ajaxBody, _ := io.ReadAll(ajaxResp.Body)
			ajaxResp.Body.Close()
			var nonceRes struct {
				Data string `json:"data"`
			}
			_ = json.Unmarshal(ajaxBody, &nonceRes)
			if nonceRes.Data != "" {
				nonce = nonceRes.Data
				nonceAction, embedAction = embedAction, nonceAction
			}
		}
	}

	// Resolve Embeds for each server
	for idx, mm := range mirrorMatches {
		rawContent := mm[1]
		serverName := strings.TrimSpace(mm[2])

		decodedJson, err := base64.StdEncoding.DecodeString(rawContent)
		if err != nil {
			continue
		}

		var payload map[string]interface{}
		if err := json.Unmarshal(decodedJson, &payload); err == nil && nonce != "" && embedAction != "" {
			embedForm := url.Values{}
			embedForm.Set("action", embedAction)
			embedForm.Set("nonce", nonce)
			for k, v := range payload {
				embedForm.Set(k, fmt.Sprintf("%v", v))
			}

			eReq, _ := http.NewRequest("POST", e.baseURL+"/wp-admin/admin-ajax.php", strings.NewReader(embedForm.Encode()))
			eReq.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
			eReq.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
			eReq.Header.Set("Referer", targetURL)
			eReq.Header.Set("X-Requested-With", "XMLHttpRequest")

			if eResp, err := e.client.Do(eReq); err == nil {
				eBody, _ := io.ReadAll(eResp.Body)
				eResp.Body.Close()

				var embedRes struct {
					Data string `json:"data"`
				}
				_ = json.Unmarshal(eBody, &embedRes)
				if embedRes.Data != "" {
					decodedIframe, err := base64.StdEncoding.DecodeString(embedRes.Data)
					if err == nil {
						iframeHTML := string(decodedIframe)
						srcRegex := regexp.MustCompile(`(?i)<iframe[^>]+src=["']([^"']+)["']`)
						if srcMatch := srcRegex.FindStringSubmatch(iframeHTML); len(srcMatch) > 1 {
							streamURL := srcMatch[1]
							quality := ""
							if qVal, ok := payload["q"].(string); ok && qVal != "" {
								quality = " " + qVal
							}

							playerTitle := fmt.Sprintf("%s%s", serverName, quality)
							videos = append(videos, client.PlayerOption{
								ID:    fmt.Sprintf("server-%d", idx+1),
								Title: playerTitle,
								Type:  "embed",
								Video: streamURL,
							})

							if defaultVideoURL == "" {
								defaultVideoURL = streamURL
							}
						}
					}
				}
			}
		}

		if len(videos) >= 6 {
			break
		}
	}

	// 4. Extract Download Formats (360p, 480p, 720p, etc.)
	var downloads []client.DownloadFormat
	dlSecRegex := regexp.MustCompile(`(?s)<div class="download">(.*?)<div class="clear">`)
	if dlSecMatch := dlSecRegex.FindStringSubmatch(html); len(dlSecMatch) > 1 {
		rowRegex := regexp.MustCompile(`(?s)<li>\s*<h4>([^<]+)</h4>\s*(.*?)\s*</li>`)
		rows := rowRegex.FindAllStringSubmatch(dlSecMatch[1], -1)

		for _, row := range rows {
			formatTitle := strings.TrimSpace(row[1])
			linksHtml := row[2]

			linkTagRegex := regexp.MustCompile(`<a\s+href="([^"]+)"[^>]*>([^<]+)</a>`)
			linkMatches := linkTagRegex.FindAllStringSubmatch(linksHtml, -1)

			var links []client.DownloadLink
			for _, lm := range linkMatches {
				dURL := strings.TrimSpace(lm[1])
				dName := strings.TrimSpace(lm[2])
				if dURL != "" && dName != "" {
					links = append(links, client.DownloadLink{
						Title: dName,
						Link:  dURL,
					})

					if defaultVideoURL == "" && strings.Contains(dURL, "pixeldrain") {
						defaultVideoURL = dURL
					}
				}
			}

			if len(links) > 0 {
				downloads = append(downloads, client.DownloadFormat{
					Format: formatTitle,
					List: []client.DownloadResolution{
						{
							Resolution: formatTitle,
							Links:      links,
						},
					},
				})
			}
		}
	}

	res := &client.EpisodeDetailResponse{
		Title:         epTitle,
		EpisodeNumber: epNum,
		VideoURL:      defaultVideoURL,
		Videos:        videos,
		Downloads:     downloads,
	}

	store.SaveEpisode(slug, res)
	return res, nil
}

// Helper utility functions
func extractSlugFromURL(rawURL string) string {
	rawURL = strings.TrimSuffix(rawURL, "/")
	parts := strings.Split(rawURL, "/")
	if len(parts) > 0 {
		return parts[len(parts)-1]
	}
	return ""
}

func cleanEpisodeNumber(title string) string {
	numRegex := regexp.MustCompile(`(?i)(?:episode|ep|eps\.?)\s*(\d+)`)
	m := numRegex.FindStringSubmatch(title)
	if len(m) > 1 {
		return m[1]
	}
	digits := regexp.MustCompile(`\d+`).FindString(title)
	if digits != "" {
		return digits
	}
	return title
}

func cleanScore(score string) string {
	score = strings.TrimSpace(score)
	if score == "" || score == "0" || score == "N/A" {
		return "8.5"
	}
	return score
}

