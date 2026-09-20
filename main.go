package main

import (
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"sync"
	"time"

	"nyamimo-go/client"
)

type SEOData struct {
	MetaDescription string
	MetaKeywords    string
	OgImage         string
	CanonicalURL    string
	OgType          string
	JSONLD          template.JS
}

type LazySectionConfig struct {
	ID    string
	Title string
}

type SectionViewData struct {
	ID          string
	Title       string
	Variant     string
	SeeAllHref  string
	AnimeList   []client.AnimeItem
}

type HomePageData struct {
	SEOData
	Title            string
	CurrentPage      string
	User             *User
	HeroAnime        []client.AnimeItem
	OngoingSection   SectionViewData
	CompletedSection SectionViewData
	LazySections     []LazySectionConfig
}

type DetailPageData struct {
	SEOData
	Title              string
	CurrentPage        string
	User               *User
	AutoSwitchServer   bool
	Slug               string
	Detail             client.AnimeDetailData
	BatchDownloads     []client.DownloadFormat
	ActiveEpisodeNum   string
	ActiveEpisodeTitle string
	ActiveVideoURL     string
	ActiveRawIframe    template.HTML
	RawIframe          template.HTML
	VideoURL           string
	IsDirectVideo      bool
	Videos             []client.PlayerOption
	GroupedVideos      map[string][]client.PlayerOption
}

type PopularPageData struct {
	SEOData
	Title        string
	CurrentPage  string
	User         *User
	CurrentOrder string
	Orders       []client.OrderOption
	AnimeList    []client.AnimeItem
	SearchQuery  string
}

type GenresPageData struct {
	SEOData
	Title          string
	CurrentPage    string
	User           *User
	CurrentGenreID string
	SelectedGenre  *client.Genre
	Genres         []client.Genre
	AnimeList      []client.AnimeItem
}

type TypePageData struct {
	SEOData
	Title       string
	CurrentPage string
	User        *User
	CurrentType string
	TypeName    string
	AnimeList   []client.AnimeItem
}

type SchedulePageData struct {
	SEOData
	Title       string
	CurrentPage string
	User        *User
	CurrentDay  string
	AnimeList   []client.AnimeItem
}

type User struct {
	Username         string `json:"username"`
	Password         string `json:"password"`
	Name             string `json:"name"`
	Role             string `json:"role"` // "admin" or "user"
	AutoSwitchServer *bool  `json:"auto_switch_server,omitempty"`
}

type ProfilePageData struct {
	SEOData
	Title            string
	CurrentPage      string
	User             *User
	AutoSwitchServer bool
}

type AuthPageData struct {
	SEOData
	Title        string
	CurrentPage  string
	User         *User
	ErrorMessage string
}

type AdminDashboardData struct {
	SEOData
	Title              string
	CurrentPage        string
	User               *User
	TotalAnimeCount    int
	TotalUsersCount    int
	TotalCarouselCount int
	ServerUptime       string
	UsersList          []User
	HeroAnime          []client.AnimeItem
	PopularAnime       []client.AnimeItem
}

type ResolutionOption struct {
	Quality    string `json:"quality"`
	Title      string `json:"title"`
	Path       string `json:"path"`
	IsSelected bool   `json:"is_selected"`
}

type ModalPlayerData struct {
	EpisodeNum        string
	Title             string
	VideoURL          string
	RawIframe         template.HTML
	IsDirectVideo     bool
	Videos            []client.PlayerOption
	GroupedVideos     map[string][]client.PlayerOption
	Resolutions       []ResolutionOption
	ActiveServerTitle string
	Downloads         []client.DownloadFormat
	AutoSwitchServer  bool
}

var api *client.APIClient
var customHeroCarousel []client.AnimeItem

var defaultHDHeroAnime = []client.AnimeItem{
	{
		Title:    "Mushoku Tensei: Isekai Ittara Honki Dasu Season 3",
		Slug:     "mushoku-tensei-isekai-ittara-honki-dasu-season-3",
		Img:      "https://wallpapercat.com/w/full/8/9/a/25114-1920x1080-desktop-full-hd-mushoku-tensei-jobless-reincarnation-wallpaper-image.jpg",
		Episode:  "Episode 12",
		Score:    "8.7",
		Type:     "TV Series",
		Released: "2024",
	},
	{
		Title:    "One Piece",
		Slug:     "one-piece",
		Img:      "https://wallpapercat.com/w/full/4/1/0/33422-3840x2160-desktop-4k-one-piece-background.jpg",
		Episode:  "Episode 1178",
		Score:    "8.9",
		Type:     "TV Series",
		Released: "1999",
	},
	{
		Title:    "K-On!",
		Slug:     "k-on",
		Img:      "https://wallpapercat.com/w/full/7/d/a/816753-1920x1080-desktop-full-hd-k-on-wallpaper.jpg",
		Episode:  "Episode 13",
		Score:    "8.5",
		Type:     "TV Series",
		Released: "2009",
	},
	{
		Title:    "One Piece: Wano Arc",
		Slug:     "one-piece",
		Img:      "https://wallpapercat.com/w/full/3/3/6/126937-3840x2160-desktop-4k-one-piece-background-image.jpg",
		Episode:  "Episode 1071",
		Score:    "9.1",
		Type:     "TV Series",
		Released: "2023",
	},
	{
		Title:    "K-On! Live Concert",
		Slug:     "k-on",
		Img:      "https://wallpapercat.com/w/full/1/b/b/816777-3840x2160-desktop-4k-k-on-background-photo.jpg",
		Episode:  "Special",
		Score:    "8.8",
		Type:     "TV Series",
		Released: "2011",
	},
}

var serverStartTime = time.Now()

var (
	usersDb = map[string]User{
		"admin": {
			Username: "admin",
			Password: "admin123",
			Name:     "Administrator Nyamimo",
			Role:     "admin",
		},
		"user": {
			Username: "user",
			Password: "user123",
			Name:     "Member Nyamimo",
			Role:     "user",
		},
	}
	usersDbLock sync.RWMutex
)

func getLoggedInUser(r *http.Request) *User {
	cookie, err := r.Cookie("user_session")
	if err != nil || cookie.Value == "" {
		return nil
	}
	usersDbLock.RLock()
	defer usersDbLock.RUnlock()
	if u, ok := usersDb[cookie.Value]; ok {
		return &u
	}
	return nil
}

func main() {
	// Initialize API client with 10-minute cache TTL
	api = client.NewAPIClient(10 * time.Minute)

	mux := http.NewServeMux()

	// Page Routes
	mux.HandleFunc("/", handleHome)
	mux.HandleFunc("/anime/", handleAnimeDetail)
	mux.HandleFunc("/popular", handlePopular)
	mux.HandleFunc("/search", handleSearchPage)
	mux.HandleFunc("/genres", handleGenres)
	mux.HandleFunc("/genres/", handleGenreDetail)
	mux.HandleFunc("/type", handleType)
	mux.HandleFunc("/type/", handleTypeDetail)
	mux.HandleFunc("/schedule", handleSchedule)
	mux.HandleFunc("/profile", handleProfile)
	mux.HandleFunc("/profile/settings", handleProfileSettings)
	mux.HandleFunc("/admin", handleAdminDashboard)
	mux.HandleFunc("/admin/dashboard", handleAdminDashboard)
	mux.HandleFunc("/admin/carousel", handleAdminCarousel)

	// SEO Routes
	mux.HandleFunc("/robots.txt", handleRobotsTXT)
	mux.HandleFunc("/sitemap.xml", handleSitemapXML)

	// Auth Routes
	mux.HandleFunc("/login", handleLogin)
	mux.HandleFunc("/api/login", handleLoginAPI)
	mux.HandleFunc("/api/google-login", handleGoogleLoginAPI)
	mux.HandleFunc("/register", handleRegister)
	mux.HandleFunc("/api/register", handleRegisterAPI)
	mux.HandleFunc("/logout", handleLogout)

	// Admin API Endpoints
	mux.HandleFunc("/api/admin/clear-cache", handleAdminClearCache)
	mux.HandleFunc("/api/admin/user/delete", handleAdminUserDelete)
	mux.HandleFunc("/api/admin/carousel/add", handleAdminCarouselAdd)
	mux.HandleFunc("/api/admin/carousel/delete", handleAdminCarouselDelete)
	mux.HandleFunc("/api/admin/carousel/reset", handleAdminCarouselReset)
	mux.HandleFunc("/api/admin/wallpaper-search", handleWallpaperSearch)

	// HTMX Partial API Endpoints
	mux.HandleFunc("/api/section/genre", handleSectionGenre)
	mux.HandleFunc("/api/search-suggest", handleSearchSuggest)
	mux.HandleFunc("/api/notifications", handleNotifications)
	mux.HandleFunc("/api/episode-modal", handleEpisodeModal)
	mux.HandleFunc("/api/episode-inline", handleEpisodeInline)
	mux.HandleFunc("/api/video-url", handleVideoURL)
	mux.HandleFunc("/api/proxy-player", handleProxyPlayer)
	// Static Files (Logo, Assets)
	fs := http.FileServer(http.Dir("public"))
	mux.Handle("/static/", http.StripPrefix("/static/", fs))
	mux.HandleFunc("/favicon.ico", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, "public/logo.png")
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "3000"
	}
	fmt.Printf("🚀 Server Go + HTMX running on http://localhost:%s\n", port)
	log.Fatal(http.ListenAndServe(":"+port, mux))
}

// Template Helper FuncMap
var funcMap = template.FuncMap{
	"mod": func(i, j int) int {
		return i % j
	},
	"add": func(i, j int) int {
		return i + j
	},
	"cleanHDImg": client.GetCleanHDImage,
	"firstChar": func(s string) string {
		if len(s) == 0 {
			return "U"
		}
		return strings.ToUpper(string([]rune(s)[0]))
	},
	"timeAgo": func(t interface{}) string {
		return "Baru saja"
	},
	"getTotalAnimeCount": func() string {
		count := api.GetTotalAnimeCount()
		return fmt.Sprintf("%d+", count)
	},
}

// Render Helper with layout
func renderPage(w http.ResponseWriter, pageTemplate string, data interface{}) {
	tmpl, err := template.New("layout").Funcs(funcMap).ParseFiles(
		"templates/layout.html",
		"templates/navbar.html",
		"templates/footer.html",
		"templates/partials/section.html",
		"templates/"+pageTemplate,
	)
	if err != nil {
		http.Error(w, "Template render error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	err = tmpl.ExecuteTemplate(w, "layout", data)
	if err != nil {
		log.Printf("Execute error: %v", err)
	}
}

// Render Partial Helper
func renderPartial(w http.ResponseWriter, partialTemplate string, templateName string, data interface{}) {
	tmpl, err := template.New(templateName).Funcs(funcMap).ParseFiles("templates/partials/" + partialTemplate)
	if err != nil {
		http.Error(w, "Partial error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = tmpl.ExecuteTemplate(w, templateName, data)
}

// Handlers
func handleHome(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" {
		http.NotFound(w, r)
		return
	}

	var newAnimeResp client.AnimeListResponse
	_ = api.GetJSON("/new-anime", &newAnimeResp)

	var ongoingResp client.AnimeListResponse
	_ = api.GetJSON("/ongoing-anime", &ongoingResp)

	var completedResp client.AnimeListResponse
	_ = api.GetJSON("/completed-anime", &completedResp)

	var genresResp client.GenreListResponse
	_ = api.GetJSON("/genres", &genresResp)

	heroAnime := customHeroCarousel
	if len(heroAnime) == 0 {
		heroAnime = defaultHDHeroAnime
	}

	for i := range ongoingResp.Data {
		ongoingResp.Data[i].Slug = client.GetAnimeSlug(ongoingResp.Data[i])
		ongoingResp.Data[i].Score = client.FormatScore(ongoingResp.Data[i].Score)
	}

	for i := range completedResp.Data {
		completedResp.Data[i].Slug = client.GetAnimeSlug(completedResp.Data[i])
		completedResp.Data[i].Score = client.FormatScore(completedResp.Data[i].Score)
	}

	var lazySections []LazySectionConfig
	for _, g := range genresResp.Data {
		lazySections = append(lazySections, LazySectionConfig{
			ID:    g.ID,
			Title: g.Title,
		})
	}

	data := HomePageData{
		SEOData: SEOData{
			MetaDescription: "Nonton anime subtitle Indonesia gratis tanpa iklan kualitas HD 1080p & 4K di Nyamimo. Update episode terbaru setiap hari, player lancar & hemat kuota.",
			MetaKeywords:    "nyamimo, nonton anime sub indo, stream anime gratis, anime subtitle indonesia, anime sub indo hd, download anime sub indo, animeindo, otakudesu, bstation, anime 2026",
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/",
			OgType:          "website",
		},
		Title:       "Nonton Anime Subtitle Indonesia Gratis HD",
		CurrentPage: "home",
		User:        getLoggedInUser(r),
		HeroAnime:   heroAnime,
		OngoingSection: SectionViewData{
			ID:         "ongoing",
			Title:      "On Going Anime",
			Variant:    "ongoing",
			SeeAllHref: "/popular?order=latest-update",
			AnimeList:  ongoingResp.Data,
		},
		CompletedSection: SectionViewData{
			ID:         "completed",
			Title:      "Completed Anime",
			Variant:    "completed",
			SeeAllHref: "/popular?order=popular",
			AnimeList:  completedResp.Data,
		},
		LazySections: lazySections,
	}

	renderPage(w, "index.html", data)
}

func handleAnimeDetail(w http.ResponseWriter, r *http.Request) {
	slug := strings.TrimPrefix(r.URL.Path, "/anime/")
	slug = strings.Trim(slug, "/")
	if slug == "" {
		http.Redirect(w, r, "/", http.StatusSeeOther)
		return
	}

	var detail client.AnimeDetailData
	err := api.GetJSON("/detail-anime/"+slug, &detail)
	if err != nil || detail.Title == "" {
		var detailWrapper client.AnimeDetailResponse
		_ = api.GetJSON("/detail-anime/"+slug, &detailWrapper)
		detail = detailWrapper.Data
	}

	if detail.Title == "" {
		http.Error(w, "Anime not found", http.StatusNotFound)
		return
	}

	if detail.Synopsis == "" && len(detail.Descriptions) > 0 {
		detail.Synopsis = strings.Join(detail.Descriptions, "\n\n")
	}

	if detail.Rating != "" {
		detail.Score = detail.Rating
	} else {
		detail.Score = client.FormatScore(detail.Score)
	}

	for i := range detail.Genres {
		if detail.Genres[i].Title == "" && detail.Genres[i].Tag != "" {
			detail.Genres[i].Title = detail.Genres[i].Tag
		}
		if detail.Genres[i].ID == "" && detail.Genres[i].Link != "" {
			parts := strings.Split(strings.Trim(detail.Genres[i].Link, "/"), "/")
			if len(parts) > 0 {
				detail.Genres[i].ID = parts[len(parts)-1]
			}
		}
	}

	for i := range detail.Episodes {
		detail.Episodes[i].Number = client.FormatEpisodeNum(detail.Episodes[i].Episode)
	}

	for i := range detail.Recommendations {
		detail.Recommendations[i].Slug = client.GetAnimeSlug(detail.Recommendations[i])
		detail.Recommendations[i].Score = client.FormatScore(detail.Recommendations[i].Score)
	}

	var downloads []client.DownloadFormat
	var dlResp struct {
		Downloads []client.DownloadFormat `json:"downloads"`
	}
	_ = api.GetJSON("/download-anime/"+slug, &dlResp)
	downloads = dlResp.Downloads

	// Default active episode video loading for inline player
	activeEpIdx := 0
	epParam := r.URL.Query().Get("ep")
	if epParam != "" {
		for idx, epItem := range detail.Episodes {
			if epItem.Number == epParam || epItem.Episode == epParam {
				activeEpIdx = idx
				break
			}
		}
	} else if len(detail.Episodes) > 0 {
		// Default to Episode 1 (Episode Awal) instead of latest episode
		foundFirstEp := false
		for idx, epItem := range detail.Episodes {
			if epItem.Number == "1" || epItem.Episode == "1" || epItem.Number == "01" {
				activeEpIdx = idx
				foundFirstEp = true
				break
			}
		}
		if !foundFirstEp {
			// Fallback to the last element if list is sorted descending
			activeEpIdx = len(detail.Episodes) - 1
		}
	}

	var activeVideoURL string
	var activeRawIframe template.HTML
	var activeVideos []client.PlayerOption
	var activeGroupedVideos map[string][]client.PlayerOption
	activeEpNum := "1"
	activeEpTitle := ""

	detail.Title = client.CleanAnimeTitle(detail.Title)

	if len(detail.Episodes) > 0 && activeEpIdx < len(detail.Episodes) {
		targetEp := detail.Episodes[activeEpIdx]
		activeEpNum = targetEp.Number
		
		subTitle := client.CleanAnimeTitle(targetEp.Title)
		if strings.Contains(subTitle, detail.Title) {
			subTitle = strings.ReplaceAll(subTitle, detail.Title, "")
		}
		if idx := strings.Index(strings.ToLower(subTitle), "episode"); idx != -1 {
			subTitle = subTitle[:idx]
		}
		subTitle = strings.Trim(strings.TrimSpace(subTitle), " :-—–")
		activeEpTitle = subTitle

		var epsDetail client.EpisodeDetailResponse
		_ = api.GetJSON(targetEp.DetailEps, &epsDetail)

		if len(epsDetail.Videos) > 0 {
			activeVideos = epsDetail.Videos
			var vidResp struct {
				URL      string `json:"url"`
				Response string `json:"response"`
			}
			_ = api.GetJSON(epsDetail.Videos[0].Video, &vidResp)
			activeVideoURL = vidResp.URL
			activeRawIframe = template.HTML(vidResp.Response)
		} else if epsDetail.VideoURL != "" && epsDetail.VideoURL != "belum tersedia (segera)" {
			activeVideoURL = epsDetail.VideoURL
		}

		activeGroupedVideos = make(map[string][]client.PlayerOption)
		for _, v := range epsDetail.Videos {
			fields := strings.Fields(v.Title)
			provider := "Server Video"
			if len(fields) > 0 {
				provider = fields[0]
			}
			activeGroupedVideos[provider] = append(activeGroupedVideos[provider], v)
		}
	}

	synopsisClean := detail.Synopsis
	if len(synopsisClean) > 160 {
		synopsisClean = synopsisClean[:157] + "..."
	}
	synopsisClean = strings.ReplaceAll(synopsisClean, "\n", " ")

	user := getLoggedInUser(r)
	autoSwitch := getAutoSwitchServerSetting(r, user)

	data := DetailPageData{
		SEOData: SEOData{
			MetaDescription: fmt.Sprintf("Nonton streaming anime %s Subtitle Indonesia gratis kualitas HD. %s", detail.Title, synopsisClean),
			MetaKeywords:    fmt.Sprintf("%s, nonton %s sub indo, stream %s, download %s sub indo hd, nyamimo %s", detail.Title, detail.Title, detail.Title, detail.Title, detail.Title),
			OgImage:         detail.Img,
			CanonicalURL:    "https://nyamimo.onrender.com/anime/" + slug,
			OgType:          "video.other",
			JSONLD:          generateAnimeDetailJSONLD(detail, slug),
		},
		Title:              "Nonton " + detail.Title + " Sub Indo HD",
		CurrentPage:        "detail",
		User:               user,
		AutoSwitchServer:   autoSwitch,
		Slug:               slug,
		Detail:             detail,
		BatchDownloads:     downloads,
		ActiveEpisodeNum:   activeEpNum,
		ActiveEpisodeTitle: activeEpTitle,
		ActiveVideoURL:     activeVideoURL,
		ActiveRawIframe:    activeRawIframe,
		RawIframe:          activeRawIframe,
		VideoURL:           activeVideoURL,
		IsDirectVideo:      isDirectStreamURL(activeVideoURL, string(activeRawIframe)),
		Videos:             activeVideos,
		GroupedVideos:      activeGroupedVideos,
	}

	renderPage(w, "anime_detail.html", data)
}

func generateAnimeDetailJSONLD(detail client.AnimeDetailData, slug string) template.JS {
	var genreTitles []string
	for _, g := range detail.Genres {
		if g.Title != "" {
			genreTitles = append(genreTitles, g.Title)
		}
	}
	genresJSON, _ := json.Marshal(genreTitles)

	synopsisClean := strings.ReplaceAll(detail.Synopsis, "\"", "\\\"")
	synopsisClean = strings.ReplaceAll(synopsisClean, "\n", " ")
	titleEsc := strings.ReplaceAll(detail.Title, "\"", "\\\"")

	jsonStr := fmt.Sprintf(`{
	  "@context": "https://schema.org",
	  "@graph": [
	    {
	      "@type": "TVSeries",
	      "name": "%s",
	      "url": "https://nyamimo.onrender.com/anime/%s",
	      "image": "%s",
	      "description": "%s",
	      "genre": %s,
	      "aggregateRating": {
	        "@type": "AggregateRating",
	        "ratingValue": "%s",
	        "bestRating": "10",
	        "worstRating": "1",
	        "ratingCount": "100"
	      }
	    },
	    {
	      "@type": "BreadcrumbList",
	      "itemListElement": [
	        {
	          "@type": "ListItem",
	          "position": 1,
	          "name": "Beranda",
	          "item": "https://nyamimo.onrender.com/"
	        },
	        {
	          "@type": "ListItem",
	          "position": 2,
	          "name": "Nonton Anime",
	          "item": "https://nyamimo.onrender.com/popular"
	        },
	        {
	          "@type": "ListItem",
	          "position": 3,
	          "name": "%s",
	          "item": "https://nyamimo.onrender.com/anime/%s"
	        }
	      ]
	    }
	  ]
	}`, titleEsc, slug, detail.Img, synopsisClean, string(genresJSON), detail.Score, titleEsc, slug)

	return template.JS(jsonStr)
}

func handlePopular(w http.ResponseWriter, r *http.Request) {
	order := r.URL.Query().Get("order")
	if order == "" {
		order = "popular"
	}

	var ordersResp client.OrderListResponse
	_ = api.GetJSON("/available-orders", &ordersResp)

	if len(ordersResp.Data) == 0 {
		ordersResp.Data = []client.OrderOption{
			{Title: "Populer", Order: "popular"},
			{Title: "Rating", Order: "rating"},
			{Title: "Terbaru", Order: "latest-update"},
		}
	}

	var listResp client.AnimeListResponse
	_ = api.GetJSON("/order-anime/"+order, &listResp)

	for i := range listResp.Data {
		listResp.Data[i].Slug = client.GetAnimeSlug(listResp.Data[i])
		listResp.Data[i].Score = client.FormatScore(listResp.Data[i].Score)
	}

	data := PopularPageData{
		SEOData: SEOData{
			MetaDescription: "Daftar anime paling populer, trending, dan terfavorit minggu ini dengan subtitle Indonesia di Nyamimo.",
			MetaKeywords:    "anime populer sub indo, trending anime, top anime 2026, nyamimo anime popular",
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/popular",
			OgType:          "website",
		},
		Title:        "Browse & Popular Anime Sub Indo",
		CurrentPage:  "popular",
		User:         getLoggedInUser(r),
		CurrentOrder: order,
		Orders:       ordersResp.Data,
		AnimeList:    listResp.Data,
	}

	renderPage(w, "popular.html", data)
}

func handleGenres(w http.ResponseWriter, r *http.Request) {
	var genresResp client.GenreListResponse
	_ = api.GetJSON("/genres", &genresResp)

	data := GenresPageData{
		SEOData: SEOData{
			MetaDescription: "Jelajahi anime berdasarkan genre favoritmu: Action, Romance, Isekai, Comedy, Fantasy, Slice of Life di Nyamimo.",
			MetaKeywords:    "genre anime sub indo, anime action sub indo, anime isekai, anime romance sub indo, nyamimo genres",
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/genres",
			OgType:          "website",
		},
		Title:       "Daftar Genre Anime Sub Indo",
		CurrentPage: "genres",
		User:        getLoggedInUser(r),
		Genres:      genresResp.Data,
	}

	renderPage(w, "genres.html", data)
}

func handleGenreDetail(w http.ResponseWriter, r *http.Request) {
	id := strings.TrimPrefix(r.URL.Path, "/genres/")

	var genresResp client.GenreListResponse
	_ = api.GetJSON("/genres", &genresResp)

	var selected *client.Genre
	for _, g := range genresResp.Data {
		if g.ID == id {
			selected = &g
			break
		}
	}

	var listResp client.AnimeListResponse
	_ = api.GetJSON("/genre-anime/"+id, &listResp)

	for i := range listResp.Data {
		listResp.Data[i].Slug = client.GetAnimeSlug(listResp.Data[i])
		listResp.Data[i].Score = client.FormatScore(listResp.Data[i].Score)
	}

	data := GenresPageData{
		SEOData: SEOData{
			MetaDescription: fmt.Sprintf("Kumpulan anime genre %s subtitle Indonesia gratis kualitas HD di Nyamimo.", id),
			MetaKeywords:    fmt.Sprintf("anime %s, anime genre %s sub indo, stream %s sub indo, nyamimo %s", id, id, id, id),
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/genres/" + id,
			OgType:          "website",
		},
		Title:          "Anime Genre " + id + " Sub Indo",
		CurrentPage:    "genres",
		User:           getLoggedInUser(r),
		CurrentGenreID: id,
		SelectedGenre:  selected,
		Genres:         genresResp.Data,
		AnimeList:      listResp.Data,
	}

	renderPage(w, "genres.html", data)
}

func handleType(w http.ResponseWriter, r *http.Request) {
	http.Redirect(w, r, "/type/tv", http.StatusSeeOther)
}

func handleTypeDetail(w http.ResponseWriter, r *http.Request) {
	t := strings.TrimPrefix(r.URL.Path, "/type/")
	if t == "" {
		t = "tv"
	}

	var listResp client.AnimeListResponse
	_ = api.GetJSON("/type-anime/"+t, &listResp)

	for i := range listResp.Data {
		listResp.Data[i].Slug = client.GetAnimeSlug(listResp.Data[i])
		listResp.Data[i].Score = client.FormatScore(listResp.Data[i].Score)
	}

	data := TypePageData{
		SEOData: SEOData{
			MetaDescription: fmt.Sprintf("Daftar lengkap anime format %s subtitle Indonesia gratis kualitas HD di Nyamimo.", strings.ToUpper(t)),
			MetaKeywords:    fmt.Sprintf("anime format %s, anime %s sub indo, stream anime %s, nyamimo %s", t, t, t, t),
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/type/" + t,
			OgType:          "website",
		},
		Title:       "Format Anime: " + strings.ToUpper(t) + " Sub Indo",
		CurrentPage: "type",
		User:        getLoggedInUser(r),
		CurrentType: t,
		TypeName:    strings.ToUpper(t),
		AnimeList:   listResp.Data,
	}

	renderPage(w, "type.html", data)
}

func handleSchedule(w http.ResponseWriter, r *http.Request) {
	dayMap := map[string]string{
		"senin":   "monday",
		"selasa":  "tuesday",
		"rabu":    "wednesday",
		"kamis":   "thursday",
		"jumat":   "friday",
		"sabtu":   "saturday",
		"minggu":  "sunday",
	}

	weekdayIndo := map[time.Weekday]string{
		time.Monday:    "senin",
		time.Tuesday:   "selasa",
		time.Wednesday: "rabu",
		time.Thursday:  "kamis",
		time.Friday:    "jumat",
		time.Saturday:  "sabtu",
		time.Sunday:    "minggu",
	}

	day := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("day")))
	if day == "" {
		day = weekdayIndo[time.Now().Weekday()]
		if day == "" {
			day = "senin"
		}
	}

	dayValue := dayMap[day]
	if dayValue == "" {
		dayValue = "monday"
		day = "senin"
	}

	var schedResp client.ReleaseScheduleResponse
	err := api.GetJSON("/release-schedule?day="+dayValue, &schedResp)
	if err != nil {
		log.Printf("Schedule API error: %v", err)
	}

	list := schedResp.Data
	for i := range list {
		list[i].Slug = client.GetAnimeSlug(list[i])
		list[i].Score = client.FormatScore(list[i].Score)
	}

	data := SchedulePageData{
		SEOData: SEOData{
			MetaDescription: "Jadwal tayang anime harian (Senin - Minggu) subtitle Indonesia terbaru lengkap dengan jam rilis WIB di Nyamimo.",
			MetaKeywords:    "jadwal anime sub indo, jadwal tayang anime, rilis anime harian, nyamimo jadwal",
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/schedule",
			OgType:          "website",
		},
		Title:       "Jadwal Rilis Anime Sub Indo Harian",
		CurrentPage: "schedule",
		User:        getLoggedInUser(r),
		CurrentDay:  day,
		AnimeList:   list,
	}

	renderPage(w, "schedule.html", data)
}

func getAutoSwitchServerSetting(r *http.Request, user *User) bool {
	if cookie, err := r.Cookie("auto_switch_server"); err == nil {
		if cookie.Value == "false" {
			return false
		} else if cookie.Value == "true" {
			return true
		}
	}
	if user != nil && user.AutoSwitchServer != nil {
		return *user.AutoSwitchServer
	}
	return false
}

func handleProfileSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/profile", http.StatusSeeOther)
		return
	}

	val := r.FormValue("auto_switch_server")
	enabled := val == "true"

	http.SetCookie(w, &http.Cookie{
		Name:     "auto_switch_server",
		Value:    fmt.Sprintf("%t", enabled),
		Path:     "/",
		Expires:  time.Now().Add(30 * 24 * time.Hour),
		HttpOnly: false,
	})

	currentUser := getLoggedInUser(r)
	if currentUser != nil {
		currentUser.AutoSwitchServer = &enabled
		usersDbLock.Lock()
		usersDb[currentUser.Username] = *currentUser
		usersDbLock.Unlock()
	}

	http.Redirect(w, r, "/profile", http.StatusSeeOther)
}

func handleProfile(w http.ResponseWriter, r *http.Request) {
	user := getLoggedInUser(r)
	autoSwitch := getAutoSwitchServerSetting(r, user)

	data := ProfilePageData{
		SEOData: SEOData{
			MetaDescription: "Profil Pengguna, Pengaturan Pemutar Video, dan Daftar Anime Favorit di Nyamimo.",
			MetaKeywords:    "nyamimo profile, anime bookmark, favorit anime, settings server",
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/profile",
			OgType:          "website",
		},
		Title:            "My List & Profil Saya",
		CurrentPage:      "profile",
		User:             user,
		AutoSwitchServer: autoSwitch,
	}
	renderPage(w, "profile.html", data)
}

// HTMX Intersect Handler for Genre Carousels
func handleSectionGenre(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	title := r.URL.Query().Get("title")

	var listResp client.AnimeListResponse
	_ = api.GetJSON("/genre-anime/"+id, &listResp)

	for i := range listResp.Data {
		listResp.Data[i].Slug = client.GetAnimeSlug(listResp.Data[i])
		listResp.Data[i].Score = client.FormatScore(listResp.Data[i].Score)
	}

	data := SectionViewData{
		ID:         id,
		Title:      title,
		SeeAllHref: "/genres/" + id,
		AnimeList:  listResp.Data,
	}

	renderPartial(w, "section.html", "section", data)
}

// Full Search Page Handler
func handleSearchPage(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	var results []client.AnimeItem
	if strings.TrimSpace(q) != "" {
		var searchResp client.AnimeListResponse
		_ = api.GetJSON("/search-anime?search="+client.FormatSearchQuery(q), &searchResp)
		results = searchResp.Data
		for i := range results {
			results[i].Slug = client.GetAnimeSlug(results[i])
			results[i].Score = client.FormatScore(results[i].Score)
			results[i].SeasonBadge = client.ExtractSeasonBadge(results[i].Title)
		}
	}

	data := PopularPageData{
		SEOData: SEOData{
			MetaDescription: fmt.Sprintf("Hasil pencarian anime untuk '%s' subtitle Indonesia gratis di Nyamimo.", q),
			MetaKeywords:    fmt.Sprintf("nonton %s sub indo, cari anime %s, %s sub indo, nyamimo search", q, q, q),
			OgImage:         "https://nyamimo.onrender.com/static/logo.png",
			CanonicalURL:    "https://nyamimo.onrender.com/search?q=" + url.QueryEscape(q),
			OgType:          "website",
		},
		Title:        "Hasil Pencarian: " + q,
		CurrentPage:  "search",
		User:         getLoggedInUser(r),
		CurrentOrder: "",
		Orders:       nil,
		AnimeList:    results,
		SearchQuery:  q,
	}

	renderPage(w, "popular.html", data)
}

func handleRobotsTXT(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	fmt.Fprintf(w, `User-agent: *
Allow: /
Disallow: /admin
Disallow: /api/

Sitemap: https://nyamimo.onrender.com/sitemap.xml
`)
}

func handleSitemapXML(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=3600")

	baseUrl := "https://nyamimo.onrender.com"
	now := time.Now().Format("2006-01-02")

	var sb strings.Builder
	sb.WriteString(`<?xml version="1.0" encoding="UTF-8"?>` + "\n")
	sb.WriteString(`<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">` + "\n")

	pages := []struct {
		loc        string
		priority   string
		changefreq string
	}{
		{"/", "1.0", "daily"},
		{"/popular", "0.9", "daily"},
		{"/schedule", "0.8", "daily"},
		{"/genres", "0.7", "weekly"},
		{"/type/tv", "0.7", "weekly"},
		{"/type/movie", "0.7", "weekly"},
	}

	for _, p := range pages {
		sb.WriteString(fmt.Sprintf(`  <url>
    <loc>%s%s</loc>
    <lastmod>%s</lastmod>
    <changefreq>%s</changefreq>
    <priority>%s</priority>
  </url>`+"\n", baseUrl, p.loc, now, p.changefreq, p.priority))
	}

	var ongoingResp client.AnimeListResponse
	if err := api.GetJSON("/ongoing-anime", &ongoingResp); err == nil {
		for _, item := range ongoingResp.Data {
			slug := client.GetAnimeSlug(item)
			if slug != "" {
				sb.WriteString(fmt.Sprintf(`  <url>
    <loc>%s/anime/%s</loc>
    <lastmod>%s</lastmod>
    <changefreq>daily</changefreq>
    <priority>0.8</priority>
  </url>`+"\n", baseUrl, slug, now))
			}
		}
	}

	var completedResp client.AnimeListResponse
	if err := api.GetJSON("/completed-anime", &completedResp); err == nil {
		for _, item := range completedResp.Data {
			slug := client.GetAnimeSlug(item)
			if slug != "" {
				sb.WriteString(fmt.Sprintf(`  <url>
    <loc>%s/anime/%s</loc>
    <lastmod>%s</lastmod>
    <changefreq>weekly</changefreq>
    <priority>0.7</priority>
  </url>`+"\n", baseUrl, slug, now))
			}
		}
	}

	sb.WriteString(`</urlset>`)
	io.WriteString(w, sb.String())
}

// HTMX Search Suggest Handler
func handleSearchSuggest(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))

	var results []client.AnimeItem
	var headerTitle string

	if q == "" {
		headerTitle = "Anime yang sedang tren"
		var popularResp client.AnimeListResponse
		_ = api.GetJSON("/order-anime/popular", &popularResp)
		results = popularResp.Data
		if len(results) == 0 {
			var ongoingResp client.AnimeListResponse
			_ = api.GetJSON("/ongoing-anime", &ongoingResp)
			results = ongoingResp.Data
		}
		if len(results) > 8 {
			results = results[:8]
		}
	} else {
		headerTitle = "Hasil Pencarian Anime"
		var searchResp client.AnimeListResponse
		_ = api.GetJSON("/search-anime?search="+client.FormatSearchQuery(q), &searchResp)
		results = searchResp.Data
		if len(results) > 8 {
			results = results[:8]
		}
	}

	for i := range results {
		results[i].Slug = client.GetAnimeSlug(results[i])
		results[i].Score = client.FormatScore(results[i].Score)
		results[i].SeasonBadge = client.ExtractSeasonBadge(results[i].Title)
	}

	data := struct {
		TitleHeader   string
		IsSearchQuery bool
		Results       []client.AnimeItem
	}{
		TitleHeader:   headerTitle,
		IsSearchQuery: q != "",
		Results:       results,
	}

	renderPartial(w, "search_results.html", "search_results", data)
}

// HTMX Notifications Handler
func handleNotifications(w http.ResponseWriter, r *http.Request) {
	var listResp client.AnimeListResponse
	_ = api.GetJSON("/order-anime/latest-update", &listResp)

	results := listResp.Data
	if len(results) > 8 {
		results = results[:8]
	}

	for i := range results {
		results[i].Slug = client.GetAnimeSlug(results[i])
		results[i].Score = client.FormatScore(results[i].Score)
	}

	data := struct {
		Results []client.AnimeItem
	}{
		Results: results,
	}

	renderPartial(w, "search_results.html", "search_results", data)
}

func isDirectStreamURL(urlStr, rawStr string) bool {
	combined := strings.ToLower(urlStr + " " + rawStr)
	if strings.Contains(combined, ".mp4") || strings.Contains(combined, ".m3u8") || strings.Contains(combined, "pixeldrain.com/api/file/") {
		return true
	}
	return false
}

func formatPlayerHTML(rawIframe template.HTML, videoURL string) (template.HTML, string) {
	rawStr := string(rawIframe)

	// 1. Direct MP4 file link detection (e.g. s0.wibufile.com/video01/...mp4, or any direct .mp4 URL)
	reMP4 := regexp.MustCompile(`https?://[^\s"'<>]+\.mp4(?:\?[^\s"'<>]*)?`)
	mp4Match := reMP4.FindString(videoURL)
	if mp4Match == "" {
		mp4Match = reMP4.FindString(rawStr)
	}

	// 2. Pixeldrain (Convert webpage URL/direct file to HTML5 Video element with CORS enabled)
	pixeldrainID := ""
	if strings.Contains(videoURL, "pixeldrain.com/u/") {
		parts := strings.Split(videoURL, "/u/")
		if len(parts) > 1 {
			pixeldrainID = strings.Split(parts[1], "/")[0]
		}
	} else if strings.Contains(rawStr, "pixeldrain.com/u/") {
		re := regexp.MustCompile(`pixeldrain\.com/u/([a-zA-Z0-9]+)`)
		m := re.FindStringSubmatch(rawStr)
		if len(m) > 1 {
			pixeldrainID = m[1]
		}
	} else if strings.Contains(videoURL, "pixeldrain.com/api/file/") {
		parts := strings.Split(videoURL, "/api/file/")
		if len(parts) > 1 {
			pixeldrainID = parts[1]
		}
	}

	if pixeldrainID != "" {
		directURL := fmt.Sprintf("https://pixeldrain.com/api/file/%s", pixeldrainID)
		html := fmt.Sprintf(`
		<video controls autoplay class="w-full h-full object-contain bg-black" poster="">
			<source src="%s" type="video/mp4">
			Browser kamu tidak mendukung pemutar video HTML5.
		</video>`, directURL)
		return template.HTML(html), directURL
	}

	if mp4Match != "" {
		html := fmt.Sprintf(`
		<video controls autoplay class="w-full h-full object-contain bg-black" poster="">
			<source src="%s" type="video/mp4">
			Browser kamu tidak mendukung pemutar video HTML5.
		</video>`, mp4Match)
		return template.HTML(html), mp4Match
	}

	// 3. Vidlion / Vidhide shortcode [vidlion id=XYZ]
	if strings.Contains(rawStr, "[vidlion id=") {
		re := regexp.MustCompile(`\[vidlion id=([a-zA-Z0-9]+)\]`)
		m := re.FindStringSubmatch(rawStr)
		if len(m) > 1 {
			vidID := m[1]
			embedURL := fmt.Sprintf("https://vidhidepro.com/v/%s", vidID)
			html := fmt.Sprintf(`
			<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, embedURL)
			return template.HTML(html), embedURL
		}
	}

	// 4. Blogger / Blogspot Proxy bypass for CORP headers
	if strings.Contains(videoURL, "blogger.com/video.g?token=") || strings.Contains(rawStr, "blogger.com/video.g?token=") {
		token := ""
		if strings.Contains(videoURL, "token=") {
			parts := strings.Split(videoURL, "token=")
			if len(parts) > 1 {
				token = parts[1]
			}
		} else {
			re := regexp.MustCompile(`token=([a-zA-Z0-9_-]+)`)
			m := re.FindStringSubmatch(rawStr)
			if len(m) > 1 {
				token = m[1]
			}
		}
		if token != "" {
			proxyURL := fmt.Sprintf("/api/proxy-player?token=%s", url.QueryEscape(token))
			html := fmt.Sprintf(`
			<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, proxyURL)
			return template.HTML(html), proxyURL
		}
	}

	// 5. Wibufile embed page proxy (e.g. api.wibufile.com/embed/... or wibufile.com/embed/...)
	if strings.Contains(videoURL, "wibufile.com/embed/") || strings.Contains(rawStr, "wibufile.com/embed/") {
		targetURL := videoURL
		if targetURL == "" || !strings.Contains(targetURL, "wibufile.com/embed/") {
			re := regexp.MustCompile(`https?://[^\s"'<>]*wibufile\.com/embed/[^\s"'<>]+`)
			m := re.FindString(rawStr)
			if m != "" {
				targetURL = m
			}
		}
		if targetURL != "" {
			proxyURL := fmt.Sprintf("/api/proxy-player?url=%s", url.QueryEscape(targetURL))
			html := fmt.Sprintf(`
			<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, proxyURL)
			return template.HTML(html), proxyURL
		}
	}

	// 6. Default iframe fallback
	if rawStr != "" && strings.Contains(rawStr, "<iframe") {
		return rawIframe, videoURL
	}

	if videoURL != "" {
		html := fmt.Sprintf(`
		<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, videoURL)
		return template.HTML(html), videoURL
	}

	return "", ""
}

func buildModalPlayerData(epsDetail client.EpisodeDetailResponse, ep, title string, autoSwitch bool) ModalPlayerData {
	var firstVideoURL string
	var firstIframe template.HTML
	var activeServerTitle string = "Server Utama"

	if len(epsDetail.Videos) > 0 {
		if autoSwitch {
			// Auto Switch ON: Iterate server options in provider's order until a valid stream is found
			for _, v := range epsDetail.Videos {
				var vidResp struct {
					URL      string `json:"url"`
					Response string `json:"response"`
				}
				if err := api.GetJSON(v.Video, &vidResp); err == nil {
					formattedIframe, formattedURL := formatPlayerHTML(template.HTML(vidResp.Response), vidResp.URL)
					if formattedIframe != "" || formattedURL != "" {
						firstIframe = formattedIframe
						firstVideoURL = formattedURL
						activeServerTitle = v.Title
						break
					}
				}
			}
		} else {
			// Auto Switch OFF: Strictly use Server #1 (v[0]) without switching/fallback
			v := epsDetail.Videos[0]
			var vidResp struct {
				URL      string `json:"url"`
				Response string `json:"response"`
			}
			if err := api.GetJSON(v.Video, &vidResp); err == nil {
				formattedIframe, formattedURL := formatPlayerHTML(template.HTML(vidResp.Response), vidResp.URL)
				firstIframe = formattedIframe
				firstVideoURL = formattedURL
				activeServerTitle = v.Title
			}
		}
	} else if epsDetail.VideoURL != "" && epsDetail.VideoURL != "belum tersedia (segera)" {
		formattedIframe, formattedURL := formatPlayerHTML("", epsDetail.VideoURL)
		firstIframe = formattedIframe
		firstVideoURL = formattedURL
	}

	grouped := make(map[string][]client.PlayerOption)
	var resolutions []ResolutionOption
	resMap := make(map[string]bool)

	for _, v := range epsDetail.Videos {
		fields := strings.Fields(v.Title)
		provider := "Server Video"
		if len(fields) > 0 {
			provider = fields[0]
		}
		grouped[provider] = append(grouped[provider], v)

		resTag := "HD"
		tLower := strings.ToLower(v.Title)
		if strings.Contains(tLower, "1080p") || strings.Contains(tLower, "fullhd") {
			resTag = "1080p Full HD"
		} else if strings.Contains(tLower, "720p") || strings.Contains(tLower, "mp4hd") {
			resTag = "720p HD"
		} else if strings.Contains(tLower, "480p") {
			resTag = "480p SD"
		} else if strings.Contains(tLower, "360p") {
			resTag = "360p"
		} else if strings.Contains(tLower, "4k") {
			resTag = "4K"
		} else if strings.Contains(tLower, "blogspot") {
			resTag = "Blogspot HD"
		}

		if !resMap[v.Video] {
			resMap[v.Video] = true
			resolutions = append(resolutions, ResolutionOption{
				Quality:    resTag,
				Title:      v.Title,
				Path:       v.Video,
				IsSelected: (v.Title == activeServerTitle),
			})
		}
	}

	isDirect := isDirectStreamURL(firstVideoURL, string(firstIframe))

	return ModalPlayerData{
		EpisodeNum:        ep,
		Title:             title,
		VideoURL:          firstVideoURL,
		RawIframe:         firstIframe,
		IsDirectVideo:     isDirect,
		Videos:            epsDetail.Videos,
		GroupedVideos:     grouped,
		Resolutions:       resolutions,
		ActiveServerTitle: activeServerTitle,
		Downloads:         epsDetail.Downloads,
		AutoSwitchServer:  autoSwitch,
	}
}

// HTMX Episode Modal Handler
func handleEpisodeModal(w http.ResponseWriter, r *http.Request) {
	detailEps := r.URL.Query().Get("detail_eps")
	title := r.URL.Query().Get("title")
	ep := r.URL.Query().Get("ep")

	var epsDetail client.EpisodeDetailResponse
	_ = api.GetJSON(detailEps, &epsDetail)

	autoSwitch := getAutoSwitchServerSetting(r, getLoggedInUser(r))
	data := buildModalPlayerData(epsDetail, ep, title, autoSwitch)
	renderPartial(w, "modal_player.html", "modal_player", data)
}

// HTMX Episode Inline Handler for Detail Page Player Swap
func handleEpisodeInline(w http.ResponseWriter, r *http.Request) {
	detailEps := r.URL.Query().Get("detail_eps")
	title := r.URL.Query().Get("title")
	ep := r.URL.Query().Get("ep")

	var epsDetail client.EpisodeDetailResponse
	_ = api.GetJSON(detailEps, &epsDetail)

	autoSwitch := getAutoSwitchServerSetting(r, getLoggedInUser(r))
	data := buildModalPlayerData(epsDetail, ep, title, autoSwitch)
	renderPartial(w, "anime_detail.html", "inline_player_area", data)
}

// HTMX Video URL Switcher Handler
func handleVideoURL(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Query().Get("path")
	if path == "" {
		http.Error(w, "Missing path parameter", http.StatusBadRequest)
		return
	}

	var vidResp struct {
		URL      string `json:"url"`
		Response string `json:"response"`
	}
	_ = api.GetJSON(path, &vidResp)

	formattedIframe, formattedURL := formatPlayerHTML(template.HTML(vidResp.Response), vidResp.URL)

	data := struct {
		VideoURL      string
		RawIframe     template.HTML
		IsDirectVideo bool
	}{
		VideoURL:      formattedURL,
		RawIframe:     formattedIframe,
		IsDirectVideo: isDirectStreamURL(formattedURL, string(formattedIframe)),
	}

	renderPartial(w, "modal_player.html", "iframe_player", data)
}

// Proxy handler to serve Blogger and Wibufile videos without CORP/Referer blocks
func handleProxyPlayer(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	targetURL := r.URL.Query().Get("url")

	if targetURL == "" && token != "" {
		targetURL = "https://www.blogger.com/video.g?token=" + token
	}

	if targetURL == "" {
		http.Error(w, "Missing token or url parameter", http.StatusBadRequest)
		return
	}

	req, err := http.NewRequest("GET", targetURL, nil)
	if err != nil {
		http.Error(w, "Failed to create request", http.StatusInternalServerError)
		return
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
	if strings.Contains(targetURL, "wibufile") {
		req.Header.Set("Referer", "https://samehadaku.email/")
	}

	c := &http.Client{Timeout: 10 * time.Second}
	resp, err := c.Do(req)
	if err != nil {
		http.Error(w, "Stream unavailable", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		http.Error(w, "Failed to read response body", http.StatusInternalServerError)
		return
	}

	bodyStr := string(bodyBytes)
	if strings.Contains(targetURL, "wibufile") && strings.Contains(bodyStr, "<head>") {
		bodyStr = strings.Replace(bodyStr, "<head>", "<head><base href=\"https://api.wibufile.com/\">", 1)
	}

	w.Write([]byte(bodyStr))
}

// Admin Dashboard & Management Handlers
func handleAdminDashboard(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	heroAnime := customHeroCarousel
	if len(heroAnime) == 0 {
		heroAnime = defaultHDHeroAnime
	}

	// Fetch registered users list
	usersDbLock.RLock()
	var usersList []User
	for _, u := range usersDb {
		usersList = append(usersList, u)
	}
	usersDbLock.RUnlock()

	// Fetch popular anime from API
	var popularResp client.AnimeListResponse
	_ = api.GetJSON("/popular", &popularResp)
	popularItems := popularResp.Data
	if len(popularItems) > 12 {
		popularItems = popularItems[:12]
	}
	for i := range popularItems {
		popularItems[i].Slug = client.GetAnimeSlug(popularItems[i])
		popularItems[i].Score = client.FormatScore(popularItems[i].Score)
		popularItems[i].Img = client.GetCleanHDImage(popularItems[i].Img)
	}

	uptimeDuration := time.Since(serverStartTime).Round(time.Second)
	uptimeStr := fmt.Sprintf("%dm %ds", int(uptimeDuration.Minutes()), int(uptimeDuration.Seconds())%60)
	if uptimeDuration.Hours() >= 1 {
		uptimeStr = fmt.Sprintf("%dh %dm", int(uptimeDuration.Hours()), int(uptimeDuration.Minutes())%60)
	}

	data := AdminDashboardData{
		SEOData: SEOData{
			MetaDescription: "Panel Administrator Nyamimo Anime Stream.",
		},
		Title:              "Dashboard Administrator",
		CurrentPage:        "admin",
		User:               currentUser,
		TotalAnimeCount:    api.GetTotalAnimeCount(),
		TotalUsersCount:    len(usersList),
		TotalCarouselCount: len(heroAnime),
		ServerUptime:       uptimeStr,
		UsersList:          usersList,
		HeroAnime:          heroAnime,
		PopularAnime:       popularItems,
	}

	renderPage(w, "admin_dashboard.html", data)
}

func handleAdminClearCache(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}
	api.ClearCache()
	http.Redirect(w, r, "/admin", http.StatusSeeOther)
}

func handleAdminUserDelete(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}
	r.ParseForm()
	targetUsername := strings.ToLower(strings.TrimSpace(r.FormValue("username")))
	if targetUsername != "" && targetUsername != "admin" {
		usersDbLock.Lock()
		delete(usersDb, targetUsername)
		usersDbLock.Unlock()
	}
	http.Redirect(w, r, "/admin", http.StatusSeeOther)
}

func handleAdminCarousel(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	heroAnime := customHeroCarousel
	if len(heroAnime) == 0 {
		heroAnime = defaultHDHeroAnime
	}

	data := HomePageData{
		Title:       "Pengaturan Hero Carousel Banner - Admin",
		CurrentPage: "admin",
		User:        currentUser,
		HeroAnime:   heroAnime,
	}

	renderPage(w, "admin_carousel.html", data)
}

func handleAdminCarouselAdd(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
		return
	}

	r.ParseForm()
	title := r.FormValue("title")
	slug := r.FormValue("slug")
	img := r.FormValue("img")
	episode := r.FormValue("episode")
	score := r.FormValue("score")
	animeType := r.FormValue("type")

	newItem := client.AnimeItem{
		Title:   title,
		Slug:    slug,
		Img:     client.GetCleanHDImage(img),
		Episode: episode,
		Score:   score,
		Type:    animeType,
	}

	if len(customHeroCarousel) == 0 {
		var newAnimeResp client.AnimeListResponse
		_ = api.GetJSON("/new-anime", &newAnimeResp)
		items := newAnimeResp.Data
		if len(items) > 8 {
			items = items[:8]
		}
		for i := range items {
			items[i].Slug = client.GetAnimeSlug(items[i])
			items[i].Score = client.FormatScore(items[i].Score)
			items[i].Img = client.GetCleanHDImage(items[i].Img)
		}
		customHeroCarousel = items
	}

	customHeroCarousel = append([]client.AnimeItem{newItem}, customHeroCarousel...)
	http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
}

func handleAdminCarouselDelete(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
		return
	}

	r.ParseForm()
	slug := r.FormValue("slug")

	if len(customHeroCarousel) == 0 {
		var newAnimeResp client.AnimeListResponse
		_ = api.GetJSON("/new-anime", &newAnimeResp)
		items := newAnimeResp.Data
		if len(items) > 8 {
			items = items[:8]
		}
		for i := range items {
			items[i].Slug = client.GetAnimeSlug(items[i])
			items[i].Score = client.FormatScore(items[i].Score)
			items[i].Img = client.GetCleanHDImage(items[i].Img)
		}
		customHeroCarousel = items
	}

	var filtered []client.AnimeItem
	for _, item := range customHeroCarousel {
		if item.Slug != slug {
			filtered = append(filtered, item)
		}
	}
	customHeroCarousel = filtered

	http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
}

func handleAdminCarouselReset(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
		return
	}

	customHeroCarousel = nil
	http.Redirect(w, r, "/admin/carousel", http.StatusSeeOther)
}

// User & Admin Authentication Handlers
func handleLogin(w http.ResponseWriter, r *http.Request) {
	if getLoggedInUser(r) != nil {
		http.Redirect(w, r, "/profile", http.StatusSeeOther)
		return
	}
	http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
}

func handleLoginAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	r.ParseForm()
	username := strings.ToLower(strings.TrimSpace(r.FormValue("username")))
	password := strings.TrimSpace(r.FormValue("password"))

	usersDbLock.RLock()
	user, exists := usersDb[username]
	usersDbLock.RUnlock()

	if !exists || user.Password != password {
		referer := r.Header.Get("Referer")
		if referer == "" || strings.Contains(referer, "/login") {
			referer = "/"
		}
		errMsg := url.QueryEscape("Username atau kata sandi salah")
		sep := "?"
		if strings.Contains(referer, "?") {
			sep = "&"
		}
		http.Redirect(w, r, referer+sep+"login_error="+errMsg, http.StatusSeeOther)
		return
	}

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    username,
		Path:     "/",
		HttpOnly: true,
	})

	referer := r.Header.Get("Referer")
	if referer == "" || strings.Contains(referer, "/login") {
		if user.Role == "admin" {
			referer = "/admin/carousel"
		} else {
			referer = "/"
		}
	}
	http.Redirect(w, r, referer, http.StatusSeeOther)
}

func handleGoogleLoginAPI(w http.ResponseWriter, r *http.Request) {
	username := "google_user"
	usersDbLock.Lock()
	if _, exists := usersDb[username]; !exists {
		usersDb[username] = User{
			Username: username,
			Password: "google_account",
			Name:     "Pengguna Google",
			Role:     "user",
		}
	}
	usersDbLock.Unlock()

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    username,
		Path:     "/",
		HttpOnly: true,
	})

	referer := r.Header.Get("Referer")
	if referer == "" || strings.Contains(referer, "/login") {
		referer = "/"
	}
	http.Redirect(w, r, referer, http.StatusSeeOther)
}

func handleRegister(w http.ResponseWriter, r *http.Request) {
	if getLoggedInUser(r) != nil {
		http.Redirect(w, r, "/profile", http.StatusSeeOther)
		return
	}
	http.Redirect(w, r, "/?show_register=true", http.StatusSeeOther)
}

func handleRegisterAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/?show_register=true", http.StatusSeeOther)
		return
	}

	r.ParseForm()
	name := strings.TrimSpace(r.FormValue("name"))
	username := strings.ToLower(strings.TrimSpace(r.FormValue("username")))
	password := strings.TrimSpace(r.FormValue("password"))
	referer := r.Header.Get("Referer")

	if username == "" || password == "" {
		if referer == "" || strings.Contains(referer, "/register") {
			referer = "/"
		}
		errMsg := url.QueryEscape("Mohon isi semua bidang formulir!")
		sep := "?"
		if strings.Contains(referer, "?") {
			sep = "&"
		}
		http.Redirect(w, r, referer+sep+"register_error="+errMsg, http.StatusSeeOther)
		return
	}

	usersDbLock.Lock()
	if _, exists := usersDb[username]; exists {
		usersDbLock.Unlock()
		if referer == "" || strings.Contains(referer, "/register") {
			referer = "/"
		}
		errMsg := url.QueryEscape("Username sudah terdaftar! Gunakan username lain.")
		sep := "?"
		if strings.Contains(referer, "?") {
			sep = "&"
		}
		http.Redirect(w, r, referer+sep+"register_error="+errMsg, http.StatusSeeOther)
		return
	}

	newUser := User{
		Username: username,
		Password: password,
		Name:     name,
		Role:     "user",
	}
	usersDb[username] = newUser
	usersDbLock.Unlock()

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    username,
		Path:     "/",
		HttpOnly: true,
	})

	if referer == "" || strings.Contains(referer, "/register") || strings.Contains(referer, "/login") {
		referer = "/"
	}
	http.Redirect(w, r, referer, http.StatusSeeOther)
}

func handleLogout(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
	})
	referer := r.Header.Get("Referer")
	if referer == "" || strings.Contains(referer, "/login") {
		referer = "/"
	}
	http.Redirect(w, r, referer, http.StatusSeeOther)
}

type WallpaperItem struct {
	URL        string `json:"url"`
	Resolution string `json:"resolution"`
	Title      string `json:"title"`
}

func fetchWallpaperCat(slug string) ([]WallpaperItem, error) {
	targetURL := "https://wallpapercat.com/" + slug
	req, err := http.NewRequest("GET", targetURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	c := &http.Client{Timeout: 5 * time.Second}
	resp, err := c.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("status %d", resp.StatusCode)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	bodyStr := string(bodyBytes)

	re := regexp.MustCompile(`/w/full/[a-zA-Z0-9/_.-]+\.(jpg|png|jpeg|webp)`)
	matches := re.FindAllString(bodyStr, -1)

	var items []WallpaperItem
	seen := make(map[string]bool)

	for _, m := range matches {
		parts := strings.Fields(m)
		if len(parts) > 0 {
			m = parts[0]
		}
		if seen[m] {
			continue
		}
		seen[m] = true

		fullURL := "https://wallpapercat.com" + m
		res := "Full HD 1080p"
		if strings.Contains(m, "3840x2160") || strings.Contains(m, "4k") || strings.Contains(m, "7680x4320") {
			res = "4K Ultra HD"
		} else if strings.Contains(m, "2560x1440") || strings.Contains(m, "2560x1600") {
			res = "2K QHD"
		}

		if strings.Contains(m, "mobile") {
			continue
		}

		items = append(items, WallpaperItem{
			URL:        fullURL,
			Resolution: res,
			Title:      slug,
		})

		if len(items) >= 8 {
			break
		}
	}

	return items, nil
}

func slugifyAnimeTitle(title string) string {
	t := strings.ToLower(title)
	if idx := strings.Index(t, ":"); idx != -1 {
		t = t[:idx]
	}
	t = strings.ReplaceAll(t, "!", "")
	t = strings.ReplaceAll(t, "?", "")
	t = strings.ReplaceAll(t, ",", "")
	t = strings.ReplaceAll(t, ".", "")
	t = strings.ReplaceAll(t, "'", "")
	t = strings.TrimSpace(t)

	words := strings.Fields(t)
	var cleanWords []string
	for _, w := range words {
		if w == "season" || w == "arc" || w == "movie" || w == "tv" || w == "series" || w == "sub" || w == "indo" {
			break
		}
		cleanWords = append(cleanWords, w)
	}
	if len(cleanWords) == 0 {
		return "anime"
	}
	return strings.Join(cleanWords, "-")
}

func handleWallpaperSearch(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	if q == "" {
		q = r.URL.Query().Get("title")
	}
	q = strings.TrimSpace(q)

	var items []WallpaperItem
	if q != "" {
		slugBase := slugifyAnimeTitle(q)
		candidates := []string{
			slugBase + "-wallpapers",
			slugBase + "-wallpaper",
			slugBase,
			slugBase + "-backgrounds",
		}

		for _, cand := range candidates {
			found, err := fetchWallpaperCat(cand)
			if err == nil && len(found) > 0 {
				items = found
				break
			}
		}
	}

	if len(items) == 0 {
		items = []WallpaperItem{
			{URL: "https://wallpapercat.com/w/full/8/9/a/25114-1920x1080-desktop-full-hd-mushoku-tensei-jobless-reincarnation-wallpaper-image.jpg", Resolution: "Full HD 1080p", Title: "Mushoku Tensei Landscape"},
			{URL: "https://wallpapercat.com/w/full/4/1/0/33422-3840x2160-desktop-4k-one-piece-background.jpg", Resolution: "4K Ultra HD", Title: "One Piece 4K"},
			{URL: "https://wallpapercat.com/w/full/7/d/a/816753-1920x1080-desktop-full-hd-k-on-wallpaper.jpg", Resolution: "Full HD 1080p", Title: "K-On! Concert"},
			{URL: "https://wallpapercat.com/w/full/3/3/6/126937-3840x2160-desktop-4k-one-piece-background-image.jpg", Resolution: "4K Ultra HD", Title: "Luffy Gear 5 / Wano"},
			{URL: "https://wallpapercat.com/w/full/5/3/1/141742-3840x2160-desktop-4k-naruto-wallpaper-photo.jpg", Resolution: "4K Ultra HD", Title: "Naruto Shippuden 4K"},
			{URL: "https://wallpapercat.com/w/full/1/b/b/816777-3840x2160-desktop-4k-k-on-background-photo.jpg", Resolution: "4K Ultra HD", Title: "Anime Live Concert"},
		}
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	titleLabel := q
	if titleLabel == "" {
		titleLabel = "Koleksi Rekomendasi"
	}
	fmt.Fprintf(w, `<div class="space-y-3 pt-3 border-t border-[#E2E2DC]">
		<div class="flex items-center justify-between">
			<span class="text-xs font-bold text-[#1A1A1E]">Pilih Wallpaper WallpaperCat HD ("%s"):</span>
			<span class="text-[11px] font-semibold text-[#55555B]">%d gambar HD</span>
		</div>
		<div class="grid grid-cols-2 gap-2.5 max-h-64 overflow-y-auto p-2 border border-[#E2E2DC] rounded-xl bg-[#F6F5F0]">`, template.HTMLEscapeString(titleLabel), len(items))

	for _, item := range items {
		fmt.Fprintf(w, `<div class="group relative rounded-lg overflow-hidden border border-[#E2E2DC] hover:border-[#FFCC00] cursor-pointer transition-all bg-black aspect-video shadow-sm" onclick="selectWallpaper('%s')">
			<img src="%s" alt="Wallpaper" class="w-full h-full object-cover group-hover:scale-105 transition-transform" />
			<span class="absolute top-1 left-1 px-1.5 py-0.5 rounded text-[9px] font-extrabold bg-[#FFCC00] text-[#17171B] shadow">
				%s
			</span>
			<div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
				<span class="px-2 py-1 rounded bg-[#FFCC00] text-[#17171B] text-[10px] font-extrabold shadow flex items-center gap-1">
					✓ Pilih Gambar Ini
				</span>
			</div>
		</div>`, template.HTMLEscapeString(item.URL), template.HTMLEscapeString(item.URL), template.HTMLEscapeString(item.Resolution))
	}

	fmt.Fprintf(w, `</div></div>`)
}


