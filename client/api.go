package client

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

const DefaultBaseAPIURL = "https://api.animekudesu.web.id"
var BaseAPIURL = DefaultBaseAPIURL

type CacheItem struct {
	Data      []byte
	Expiration time.Time
}

type APIClient struct {
	httpClient *http.Client
	cache      sync.Map
	ttl        time.Duration
	baseURL    string
}

func NewAPIClient(ttl time.Duration) *APIClient {
	return &APIClient{
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
		ttl:     ttl,
		baseURL: BaseAPIURL,
	}
}

func (c *APIClient) SetBaseURL(url string) {
	url = strings.TrimSpace(url)
	if url != "" {
		url = strings.TrimSuffix(url, "/")
		c.baseURL = url
		BaseAPIURL = url
		c.ClearCache()
	}
}

func (c *APIClient) GetBaseURL() string {
	if c.baseURL != "" {
		return c.baseURL
	}
	return BaseAPIURL
}

func (c *APIClient) ClearCache() {
	c.cache.Range(func(key, value interface{}) bool {
		c.cache.Delete(key)
		return true
	})
}

func (c *APIClient) GetJSON(endpoint string, target interface{}) error {
	// Clean endpoint URL
	endpoint = strings.TrimSpace(endpoint)
	if !strings.HasPrefix(endpoint, "http") {
		if !strings.HasPrefix(endpoint, "/") {
			endpoint = "/" + endpoint
		}
		endpoint = c.GetBaseURL() + endpoint
	}

	// Check cache
	if item, ok := c.cache.Load(endpoint); ok {
		cached := item.(CacheItem)
		if time.Now().Before(cached.Expiration) {
			return json.Unmarshal(cached.Data, target)
		}
		c.cache.Delete(endpoint)
	}

	req, err := http.NewRequest("GET", endpoint, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("API error: status code %d for %s", resp.StatusCode, endpoint)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	err = json.Unmarshal(body, target)
	if err != nil {
		return err
	}

	// Save to cache
	c.cache.Store(endpoint, CacheItem{
		Data:       body,
		Expiration: time.Now().Add(c.ttl),
	})

	return nil
}

func (c *APIClient) GetTotalAnimeCount() int {
	var resp struct {
		TotalPage int         `json:"total_page"`
		Data      []AnimeItem `json:"data"`
	}
	err := c.GetJSON("/order-anime/popular?page=1", &resp)
	if err == nil && resp.TotalPage > 0 {
		itemsPerPage := len(resp.Data)
		if itemsPerPage == 0 {
			itemsPerPage = 30
		}
		return resp.TotalPage * itemsPerPage
	}
	return 780
}

// Data Models
type AnimeItem struct {
	Link        string      `json:"link"`
	DetailURL   string      `json:"detail_url"`
	Slug        string      `json:"slug"`
	Img         string      `json:"img"`
	Alt         string      `json:"alt"`
	Title       string      `json:"title"`
	Episode     string      `json:"episode"`
	Released    string      `json:"released"`
	Time        string      `json:"time"`
	Type        string      `json:"type"`
	Score       interface{} `json:"score"` // Can be float64, string, or nil
	TotalViews  interface{} `json:"total_views"`
	SeasonBadge string      `json:"-"`
}

type AnimeListResponse struct {
	Status string      `json:"status"`
	Data   []AnimeItem `json:"data"`
}

type Genre struct {
	Title string `json:"title"`
	ID    string `json:"id"`
	Link  string `json:"link"`
	Tag   string `json:"tag"`
}

type GenreListResponse struct {
	Status string  `json:"status"`
	Data   []Genre `json:"data"`
}

type EpisodeRef struct {
	Title     string      `json:"title"`
	DetailEps string      `json:"detail_eps"`
	Episode   interface{} `json:"episode"`
	Number    string      `json:"-"`
}

type DownloadLink struct {
	Title string `json:"title"`
	Link  string `json:"link"`
}

type DownloadResolution struct {
	Resolution string         `json:"resolution"`
	Links      []DownloadLink `json:"links"`
}

type DownloadFormat struct {
	Format string               `json:"format"`
	List   []DownloadResolution `json:"list"`
}

type AnimeDetailData struct {
	Title           string           `json:"title"`
	AltTitle        string           `json:"alt_title"`
	EnglishTitle    string           `json:"english_title"`
	JapaneseTitle   string           `json:"japanese_title"`
	Img             string           `json:"img"`
	Rating          string           `json:"rating"`
	Score           interface{}      `json:"score"`
	Type            string           `json:"type"`
	Status          string           `json:"status"`
	Duration        string           `json:"duration"`
	Release         string           `json:"release"`
	Released        string           `json:"released"`
	Studio          string           `json:"studio"`
	Descriptions    []string         `json:"descriptions"`
	Synopsis        string           `json:"synopsis"`
	Genres          []Genre          `json:"genres"`
	Episodes        []EpisodeRef     `json:"episodes"`
	Downloads       []DownloadFormat `json:"downloads"`
	BatchLink       string           `json:"batch_link"`
	Recommendations []AnimeItem      `json:"recommendations"`
}

type AnimeDetailResponse struct {
	Status string          `json:"status"`
	Data   AnimeDetailData `json:"data"`
}

type PlayerOption struct {
	ID     string `json:"id"`
	Title  string `json:"title"`
	Post   string `json:"post"`
	Action string `json:"action"`
	Nume   string `json:"nume"`
	Type   string `json:"type"`
	Video  string `json:"video"`
}

type EpisodeDetailResponse struct {
	Title         string           `json:"title"`
	Description   string           `json:"description"`
	EpisodeNumber interface{}      `json:"episode_number"`
	VideoURL      string           `json:"video_url"`
	Videos        []PlayerOption   `json:"videos"`
	Downloads     []DownloadFormat `json:"downloads"`
}

type VideoURLResponse struct {
	URL string `json:"url"`
}

type OrderOption struct {
	Title string `json:"title"`
	Order string `json:"order"`
}

type OrderListResponse struct {
	Data []OrderOption `json:"data"`
}

type ScheduleDay struct {
	Day   string      `json:"day"`
	Anime []AnimeItem `json:"anime"`
}

type ScheduleResponse struct {
	Data []ScheduleDay `json:"data"`
}

type ReleaseScheduleResponse struct {
	Message    string      `json:"message"`
	Day        string      `json:"day"`
	DayValue   string      `json:"day_value"`
	TotalAnime int         `json:"total_anime"`
	Data       []AnimeItem `json:"data"`
}

// Helper to extract clean slug
func GetAnimeSlug(item AnimeItem) string {
	if item.Slug != "" {
		return strings.TrimPrefix(item.Slug, "/")
	}
	u := item.DetailURL
	if u == "" {
		u = item.Link
	}
	u = strings.Split(u, "?")[0]
	parts := strings.Split(strings.Trim(u, "/"), "/")
	if len(parts) > 0 {
		return parts[len(parts)-1]
	}
	return ""
}

func FormatScore(score interface{}) string {
	if score == nil {
		return "N/A"
	}
	switch v := score.(type) {
	case float64:
		if v <= 0 {
			return "N/A"
		}
		return fmt.Sprintf("%.1f", v)
	case string:
		if v == "" || v == "0" || v == "N/A" {
			return "N/A"
		}
		return v
	default:
		return "N/A"
	}
}

func FormatEpisodeNum(ep interface{}) string {
	if ep == nil {
		return "-"
	}
	switch v := ep.(type) {
	case float64:
		return fmt.Sprintf("%.0f", v)
	case string:
		if v == "" {
			return "-"
		}
		return v
	default:
		return fmt.Sprintf("%v", v)
	}
}

func FormatSearchQuery(q string) string {
	return url.QueryEscape(q)
}

func CleanAnimeTitle(t string) string {
	t = strings.TrimSpace(t)
	if idx := strings.Index(t, "Sub Indo"); idx != -1 {
		cleaned := strings.TrimSpace(t[:idx])
		if cleaned != "" {
			return cleaned
		}
	}
	return t
}

func GetCleanHDImage(img string) string {
	if img == "" {
		return ""
	}
	idx := strings.LastIndex(img, ".")
	if idx == -1 {
		return img
	}
	ext := img[idx:]
	base := img[:idx]
	dashIdx := strings.LastIndex(base, "-")
	if dashIdx != -1 {
		dim := base[dashIdx+1:]
		if strings.Contains(dim, "x") {
			parts := strings.Split(dim, "x")
			if len(parts) == 2 {
				return base[:dashIdx] + ext
			}
		}
	}
	return img
}

func ExtractSeasonBadge(title string) string {
	t := strings.TrimSpace(title)
	lower := strings.ToLower(t)

	if strings.Contains(lower, "movie") || strings.Contains(lower, "gekijouban") {
		return "Movie"
	}
	if strings.Contains(lower, "ova") || strings.Contains(lower, "oad") || strings.Contains(lower, "special") {
		return "OVA / Special"
	}

	reSeasonPart := regexp.MustCompile(`(?i)(season\s*\d+|1st\s*season|2nd\s*season|3rd\s*season|\d+th\s*season|s\d+)\s*(?:-?\s*|\s+)(part\s*\d+|cour\s*\d+)`)
	if match := reSeasonPart.FindString(t); match != "" {
		return strings.Title(strings.ToLower(match))
	}

	reSeason := regexp.MustCompile(`(?i)(season\s*\d+|1st\s*season|2nd\s*season|3rd\s*season|\d+th\s*season|season\s+[ivx]+)`)
	if match := reSeason.FindString(t); match != "" {
		return strings.Title(strings.ToLower(match))
	}

	rePart := regexp.MustCompile(`(?i)(part\s*\d+|cour\s*\d+)`)
	if match := rePart.FindString(t); match != "" {
		return strings.Title(strings.ToLower(match))
	}

	return "Season 1"
}
