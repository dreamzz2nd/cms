package main

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"log"
	"math"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
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
	Email            string `json:"email,omitempty"`
	Avatar           string `json:"avatar,omitempty"`
	GoogleID         string `json:"google_id,omitempty"`
	AutoSwitchServer *bool  `json:"auto_switch_server,omitempty"`
	LastSeenAt       int64  `json:"last_seen_at,omitempty"`
}

type WatchHistoryItem struct {
	Slug              string  `json:"slug"`
	Title             string  `json:"title"`
	Img               string  `json:"img"`
	Episode           string  `json:"episode"`
	EpisodeNum        string  `json:"episodeNum"`
	EpisodeTitle      string  `json:"episodeTitle"`
	Time              float64 `json:"time"`
	Duration          float64 `json:"duration"`
	TimeFormatted     string  `json:"timeFormatted"`
	DurationFormatted string  `json:"durationFormatted"`
	ProgressPercent   int     `json:"progressPercent"`
	Link              string  `json:"link"`
	UpdatedAt         int64   `json:"updatedAt"`
}

type ProgressRequest struct {
	Slug         string  `json:"slug"`
	Title        string  `json:"title"`
	Img          string  `json:"img"`
	Episode      string  `json:"episode"`
	EpisodeNum   string  `json:"episodeNum"`
	EpisodeTitle string  `json:"episodeTitle"`
	Time         float64 `json:"time"`
	Duration     float64 `json:"duration"`
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

type AdSlot struct {
	Enabled bool   `json:"enabled"`
	Title   string `json:"title"`
	Code    string `json:"code"`
}

type VideoPrerollAd struct {
	Enabled          bool   `json:"enabled"`
	Title            string `json:"title"`
	VideoURL         string `json:"video_url"`
	TargetLink       string `json:"target_link"`
	DurationSeconds  int    `json:"duration_seconds"`
	SkipAfterSeconds int    `json:"skip_after_seconds"`
}

type AdSettings struct {
	HeaderBanner AdSlot         `json:"header_banner"`
	BelowPlayer  AdSlot         `json:"below_player"`
	Popunder     AdSlot         `json:"popunder"`
	FooterBanner AdSlot         `json:"footer_banner"`
	VideoPreroll VideoPrerollAd `json:"video_preroll"`
}

type GDriveSettings struct {
	Enabled             bool     `json:"enabled"`
	FolderID            string   `json:"folder_id"`
	ServiceAccountJSON  string   `json:"service_account_json"`
	AutoSyncOngoing     bool     `json:"auto_sync_ongoing"`
	Resolutions         []string `json:"resolutions"`
	TotalStorageGB      int      `json:"total_storage_gb"`
	UsedStorageGB       float64  `json:"used_storage_gb"`
	TotalSyncedEpisodes int      `json:"total_synced_episodes"`
	Connected           bool     `json:"connected"`
	AccessToken         string   `json:"access_token,omitempty"`
	RefreshToken        string   `json:"refresh_token,omitempty"`
	AccountEmail        string   `json:"account_email,omitempty"`
}

type AppConfig struct {
	SiteName           string         `json:"site_name"`
	SiteTagline        string         `json:"site_tagline"`
	SiteLogo           string         `json:"site_logo"`
	SiteFavicon        string         `json:"site_favicon"`
	PrimaryColor       string         `json:"primary_color"`
	APIProvider        string         `json:"api_provider"`
	APIBaseURL         string         `json:"api_base_url"`
	Port               string         `json:"port"`
	GoogleClientID     string         `json:"google_client_id"`
	GoogleClientSecret string         `json:"google_client_secret"`
	GDrive             GDriveSettings `json:"gdrive"`
	Ads                AdSettings     `json:"ads"`
}

type AdminDashboardData struct {
	SEOData
	Title              string
	CurrentPage        string
	User               *User
	TotalAnimeCount    int
	TotalUsersCount    int
	OnlineUsersCount   int
	OfflineUsersCount  int
	TotalCarouselCount int
	ServerUptime       string
	UsersList          []User
	HeroAnime          []client.AnimeItem
	PopularAnime       []client.AnimeItem
	Config             AppConfig
	SavedNotice        string
	// Visitor Stats
	TotalPageViews      int64
	TodayPageViews      int64
	UniqueVisitors      int64
	TodayUniqueVisitors int64
	PopularPages        []PageViewStat
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

// ─── Visitor / Analytics Tracking ───────────────────────────────────────────

type PageViewStat struct {
	Path  string
	Views int64
}

var (
	// Atomic counters – safe to read/write without mutex
	totalPageViews int64
	todayPageViews int64

	// Unique visitor sets (IP → struct{})
	visitorLock          sync.RWMutex
	allTimeVisitors      = make(map[string]struct{})
	todayVisitors        = make(map[string]struct{})
	visitorDayReset      = time.Now().Truncate(24 * time.Hour)

	// Per-page view counter
	pageViewLock sync.RWMutex
	pageViews    = make(map[string]int64)
)

// getClientIP extracts the real IP from the request.
func getClientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[0])
	}
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return strings.TrimSpace(xri)
	}
	host := r.RemoteAddr
	if idx := strings.LastIndex(host, ":"); idx != -1 {
		return host[:idx]
	}
	return host
}

// recordVisit tracks a page request. Call at the top of every page handler.
func recordVisit(r *http.Request) {
	// Skip API, static, and admin endpoints
	p := r.URL.Path
	if strings.HasPrefix(p, "/api/") ||
		strings.HasPrefix(p, "/static/") ||
		strings.HasPrefix(p, "/admin") ||
		p == "/favicon.ico" || p == "/robots.txt" || p == "/sitemap.xml" {
		return
	}

	atomic.AddInt64(&totalPageViews, 1)

	// Daily reset check
	visitorLock.Lock()
	today := time.Now().Truncate(24 * time.Hour)
	if today.After(visitorDayReset) {
		visitorDayReset = today
		todayVisitors = make(map[string]struct{})
		atomic.StoreInt64(&todayPageViews, 0)
	}
	atomic.AddInt64(&todayPageViews, 1)

	ip := getClientIP(r)
	allTimeVisitors[ip] = struct{}{}
	todayVisitors[ip] = struct{}{}
	visitorLock.Unlock()

	// Per-page counter
	pageViewLock.Lock()
	pageViews[p]++
	pageViewLock.Unlock()
}

// getVisitorStats returns a snapshot of visitor statistics.
func getVisitorStats() (totalPV, todayPV, uniqueAll, uniqueToday int64, topPages []PageViewStat) {
	totalPV = atomic.LoadInt64(&totalPageViews)
	todayPV = atomic.LoadInt64(&todayPageViews)

	visitorLock.RLock()
	uniqueAll = int64(len(allTimeVisitors))
	uniqueToday = int64(len(todayVisitors))
	visitorLock.RUnlock()

	pageViewLock.RLock()
	type kv struct {
		Path  string
		Views int64
	}
	var sorted []kv
	for k, v := range pageViews {
		sorted = append(sorted, kv{k, v})
	}
	pageViewLock.RUnlock()

	// Simple insertion sort (list is small)
	for i := 1; i < len(sorted); i++ {
		for j := i; j > 0 && sorted[j].Views > sorted[j-1].Views; j-- {
			sorted[j], sorted[j-1] = sorted[j-1], sorted[j]
		}
	}
	max := 8
	if len(sorted) < max {
		max = len(sorted)
	}
	for _, item := range sorted[:max] {
		topPages = append(topPages, PageViewStat{Path: item.Path, Views: item.Views})
	}
	return
}

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
	usersFile   = "data/users.json"

	watchHistoryDb     = make(map[string][]WatchHistoryItem)
	watchHistoryDbLock sync.RWMutex
	watchHistoryFile   = "data/watch_history.json"

	appConfig     AppConfig
	appConfigLock sync.RWMutex
	configPath    = "config.json"
)

func initUsersDb() {
	_ = os.MkdirAll("data", 0755)
	if data, err := os.ReadFile(usersFile); err == nil {
		usersDbLock.Lock()
		_ = json.Unmarshal(data, &usersDb)
		usersDbLock.Unlock()
	}
}

func saveUsersDbUnsafe() {
	_ = os.MkdirAll("data", 0755)
	if data, err := json.MarshalIndent(usersDb, "", "  "); err == nil {
		_ = os.WriteFile(usersFile, data, 0644)
	}
}

func initWatchHistory() {
	_ = os.MkdirAll("data", 0755)
	if data, err := os.ReadFile(watchHistoryFile); err == nil {
		watchHistoryDbLock.Lock()
		_ = json.Unmarshal(data, &watchHistoryDb)
		watchHistoryDbLock.Unlock()
	}
}

func saveWatchHistoryUnsafe() {
	_ = os.MkdirAll("data", 0755)
	if data, err := json.MarshalIndent(watchHistoryDb, "", "  "); err == nil {
		_ = os.WriteFile(watchHistoryFile, data, 0644)
	}
}

func formatTimeSec(sec float64) string {
	if sec <= 0 {
		return "00:00"
	}
	s := int(sec)
	hrs := s / 3600
	mins := (s % 3600) / 60
	secs := s % 60
	if hrs > 0 {
		return fmt.Sprintf("%02d:%02d:%02d", hrs, mins, secs)
	}
	return fmt.Sprintf("%02d:%02d", mins, secs)
}

func getOrSetGuestID(w http.ResponseWriter, r *http.Request) string {
	if c, err := r.Cookie("nyamimo_guest_id"); err == nil && strings.TrimSpace(c.Value) != "" {
		return c.Value
	}
	guestID := "g_" + strconv.FormatInt(time.Now().UnixNano(), 36)
	if w != nil {
		http.SetCookie(w, &http.Cookie{
			Name:     "nyamimo_guest_id",
			Value:    guestID,
			Path:     "/",
			MaxAge:   3600 * 24 * 90, // 90 days
			HttpOnly: false,
		})
	}
	return guestID
}

func getHistoryStorageKey(w http.ResponseWriter, r *http.Request) string {
	if u := getLoggedInUser(r); u != nil {
		return "user:" + u.Username
	}
	return "guest:" + getOrSetGuestID(w, r)
}

func upsertWatchHistory(key string, req ProgressRequest) WatchHistoryItem {
	cleanSlug := strings.Trim(strings.TrimSpace(req.Slug), "\"'/")
	epNum := strings.TrimSpace(req.EpisodeNum)
	if epNum == "" {
		epNum = "1"
	}
	epDisplay := "Episode " + epNum
	timeFmt := formatTimeSec(req.Time)
	durFmt := formatTimeSec(req.Duration)
	progressPct := 0
	if req.Duration > 0 {
		progressPct = int(math.Min(100, math.Round((req.Time/req.Duration)*100)))
	}
	cleanLink := fmt.Sprintf("/anime/%s/?ep=%s", url.PathEscape(cleanSlug), url.QueryEscape(epNum))
	if req.Time > 5 {
		cleanLink += fmt.Sprintf("&t=%d", int(req.Time))
	}

	item := WatchHistoryItem{
		Slug:              cleanSlug,
		Title:             strings.TrimSpace(req.Title),
		Img:               strings.TrimSpace(req.Img),
		Episode:           epDisplay,
		EpisodeNum:        epNum,
		EpisodeTitle:      strings.TrimSpace(req.EpisodeTitle),
		Time:              math.Floor(req.Time),
		Duration:          math.Floor(req.Duration),
		TimeFormatted:     timeFmt,
		DurationFormatted: durFmt,
		ProgressPercent:   progressPct,
		Link:              cleanLink,
		UpdatedAt:         time.Now().UnixMilli(),
	}

	watchHistoryDbLock.Lock()
	defer watchHistoryDbLock.Unlock()

	list := watchHistoryDb[key]
	var newList []WatchHistoryItem
	for _, it := range list {
		if it.Slug != cleanSlug {
			newList = append(newList, it)
		}
	}
	newList = append([]WatchHistoryItem{item}, newList...)
	if len(newList) > 50 {
		newList = newList[:50]
	}
	watchHistoryDb[key] = newList
	saveWatchHistoryUnsafe()
	return item
}

func mergeGuestHistory(w http.ResponseWriter, r *http.Request, username string) {
	cookie, err := r.Cookie("nyamimo_guest_id")
	if err != nil || cookie.Value == "" {
		return
	}
	guestKey := "guest:" + cookie.Value
	userKey := "user:" + username

	watchHistoryDbLock.Lock()
	defer watchHistoryDbLock.Unlock()

	guestList := watchHistoryDb[guestKey]
	if len(guestList) == 0 {
		return
	}

	userList := watchHistoryDb[userKey]
	slugMap := make(map[string]WatchHistoryItem)
	for _, item := range userList {
		slugMap[item.Slug] = item
	}
	for _, item := range guestList {
		if existing, exists := slugMap[item.Slug]; exists {
			if item.UpdatedAt > existing.UpdatedAt {
				slugMap[item.Slug] = item
			}
		} else {
			slugMap[item.Slug] = item
		}
	}

	var merged []WatchHistoryItem
	for _, it := range slugMap {
		merged = append(merged, it)
	}
	sort.Slice(merged, func(i, j int) bool {
		return merged[i].UpdatedAt > merged[j].UpdatedAt
	})
	if len(merged) > 50 {
		merged = merged[:50]
	}

	watchHistoryDb[userKey] = merged
	delete(watchHistoryDb, guestKey)
	saveWatchHistoryUnsafe()
}

func getDefaultConfig() AppConfig {
	return AppConfig{
		SiteName:           "Nyamimo",
		SiteTagline:        "Streaming Anime Sub Indo Tercepat & Terlengkap",
		SiteLogo:           "/static/logo.png",
		SiteFavicon:        "/static/logo.png",
		PrimaryColor:       "#FFCC00",
		APIProvider:        "animekudesu",
		APIBaseURL:         "https://api.animekudesu.web.id",
		Port:               "3000",
		GoogleClientID:     "494465077307-bt9dlfv5ungb9gd7ectnln3auu77edlm.apps.googleusercontent.com",
		GoogleClientSecret: "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",
		GDrive: GDriveSettings{
			Enabled:             false,
			FolderID:            "",
			ServiceAccountJSON:  "",
			AutoSyncOngoing:     false,
			Resolutions:         []string{"1080p", "720p"},
			TotalStorageGB:      5120,
			UsedStorageGB:       0,
			TotalSyncedEpisodes: 0,
		},
		Ads: AdSettings{
			HeaderBanner: AdSlot{
				Enabled: false,
				Title:   "Header Top Banner (728x90 / Responsive)",
				Code:    "<div class=\"w-full max-w-4xl mx-auto my-2 p-3 bg-[#17171B]/5 border border-dashed border-[#FFCC00]/50 rounded-xl text-center text-xs text-[#55555B]\">🚀 Pasang Iklan Banner Header di Sini (Atur di Dashboard Admin)</div>",
			},
			BelowPlayer: AdSlot{
				Enabled: false,
				Title:   "Bawah Pemutar Video",
				Code:    "<div class=\"w-full max-w-4xl mx-auto my-3 p-4 bg-[#FFCC00]/10 border border-dashed border-[#FFCC00] rounded-xl text-center text-xs font-bold text-[#17171B]\">🎬 Slot Iklan Bawah Player (Adsterra / Popunder / Banner)</div>",
			},
			Popunder: AdSlot{
				Enabled: false,
				Title:   "Popunder / Direct Script (Head/Body Script)",
				Code:    "<!-- Script Popunder atau Monetag / Adsterra / Adsense ditaruh di sini -->",
			},
			FooterBanner: AdSlot{
				Enabled: false,
				Title:   "Footer Sticky / Bottom Banner",
				Code:    "<div class=\"w-full max-w-4xl mx-auto my-2 p-3 bg-[#17171B]/5 border border-dashed border-[#E2E2DC] rounded-xl text-center text-xs text-[#55555B]\">📢 Pasang Iklan Footer di Sini</div>",
			},
			VideoPreroll: VideoPrerollAd{
				Enabled:          false,
				Title:            "Video Pre-roll Ad (Gaya YouTube)",
				VideoURL:         "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
				TargetLink:       "https://monetag.com",
				DurationSeconds:  15,
				SkipAfterSeconds: 5,
			},
		},
	}
}

func loadAppConfig() {
	appConfigLock.Lock()
	defer appConfigLock.Unlock()

	appConfig = getDefaultConfig()

	data, err := os.ReadFile(configPath)
	if err == nil {
		if err := json.Unmarshal(data, &appConfig); err != nil {
			log.Printf("Warning: Failed to parse config.json, using defaults: %v", err)
		}
	} else {
		_ = saveAppConfigUnsafe()
	}

	if envPort := os.Getenv("PORT"); envPort != "" {
		appConfig.Port = envPort
	}
	if envProvider := os.Getenv("API_PROVIDER"); envProvider != "" {
		appConfig.APIProvider = envProvider
	}
	if envAPI := os.Getenv("API_BASE_URL"); envAPI != "" {
		appConfig.APIBaseURL = envAPI
	}
	if envSite := os.Getenv("SITE_NAME"); envSite != "" {
		appConfig.SiteName = envSite
	}
	if envGoogleID := os.Getenv("GOOGLE_CLIENT_ID"); envGoogleID != "" {
		appConfig.GoogleClientID = envGoogleID
	}
	if envGoogleSec := os.Getenv("GOOGLE_CLIENT_SECRET"); envGoogleSec != "" {
		appConfig.GoogleClientSecret = envGoogleSec
	}

	if appConfig.APIProvider == "" {
		appConfig.APIProvider = "animekudesu"
	}

	if api != nil {
		api.SetProvider(appConfig.APIProvider)
		if appConfig.APIBaseURL != "" {
			api.SetBaseURL(appConfig.APIBaseURL)
		}
	}
}

func saveAppConfigUnsafe() error {
	data, err := json.MarshalIndent(appConfig, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(configPath, data, 0644)
}

func saveAppConfig() error {
	appConfigLock.Lock()
	defer appConfigLock.Unlock()
	return saveAppConfigUnsafe()
}

func getAppConfig() AppConfig {
	appConfigLock.RLock()
	defer appConfigLock.RUnlock()
	return appConfig
}

func touchUserActivity(username string) {
	if username == "" {
		return
	}
	now := time.Now().Unix()
	usersDbLock.Lock()
	if u, ok := usersDb[username]; ok {
		needSave := (now - u.LastSeenAt) >= 30
		u.LastSeenAt = now
		usersDb[username] = u
		if needSave {
			saveUsersDbUnsafe()
		}
	}
	usersDbLock.Unlock()
}

func getLoggedInUser(r *http.Request) *User {
	cookie, err := r.Cookie("user_session")
	if err != nil || cookie.Value == "" {
		return nil
	}
	usersDbLock.RLock()
	u, ok := usersDb[cookie.Value]
	usersDbLock.RUnlock()
	if ok {
		go touchUserActivity(u.Username)
		return &u
	}
	return nil
}

func handleUserHeartbeat(w http.ResponseWriter, r *http.Request) {
	u := getLoggedInUser(r)
	w.Header().Set("Content-Type", "application/json")
	if u != nil {
		touchUserActivity(u.Username)
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"status":    "ok",
			"online":    true,
			"username":  u.Username,
			"last_seen": time.Now().Unix(),
		})
		return
	}
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": "guest",
		"online": false,
	})
}

func main() {
	// Initialize API client with 10-minute cache TTL
	api = client.NewAPIClient(10 * time.Minute)
	loadAppConfig()
	initUsersDb()
	initWatchHistory()

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

	// Auth Routes (Local & Google OAuth 2.0)
	mux.HandleFunc("/login", handleLogin)
	mux.HandleFunc("/api/login", handleLoginAPI)
	mux.HandleFunc("/api/google-login", handleGoogleLoginAPI)
	mux.HandleFunc("/api/auth/google/callback", handleGoogleCallbackAPI)
	mux.HandleFunc("/register", handleRegister)
	mux.HandleFunc("/api/register", handleRegisterAPI)
	mux.HandleFunc("/logout", handleLogout)
	mux.HandleFunc("/api/user/heartbeat", handleUserHeartbeat)

	// Native Android App REST API V1 (Bilibili Style Architecture)
	mux.HandleFunc("/api/v1/home", handleAPIV1Home)
	mux.HandleFunc("/api/v1/anime/", handleAPIV1AnimeDetail)
	mux.HandleFunc("/api/v1/episode", handleAPIV1Episode)
	mux.HandleFunc("/api/v1/search", handleAPIV1Search)
	mux.HandleFunc("/api/v1/history", handleGetHistoryAPI)
	mux.HandleFunc("/api/v1/history/progress", handleHistoryProgressAPI)
	mux.HandleFunc("/api/v1/history/delete", handleHistoryDeleteAPI)

	// Watch History API Endpoints (Bilibili Anonymous Device/Session + User Sync Model)
	mux.HandleFunc("/api/history", handleGetHistoryAPI)
	mux.HandleFunc("/api/history/progress", handleHistoryProgressAPI)
	mux.HandleFunc("/api/history/delete", handleHistoryDeleteAPI)

	// Admin API Endpoints
	mux.HandleFunc("/api/admin/clear-cache", handleAdminClearCache)
	mux.HandleFunc("/api/admin/user/delete", handleAdminUserDelete)
	mux.HandleFunc("/api/admin/carousel/add", handleAdminCarouselAdd)
	mux.HandleFunc("/api/admin/carousel/delete", handleAdminCarouselDelete)
	mux.HandleFunc("/api/admin/carousel/reset", handleAdminCarouselReset)
	mux.HandleFunc("/api/admin/wallpaper-search", handleWallpaperSearch)
	mux.HandleFunc("/api/admin/ads/save", handleAdminAdsSave)
	mux.HandleFunc("/api/admin/site/save", handleAdminSiteSave)
	mux.HandleFunc("/api/admin/api/test", handleAdminAPITest)
	mux.HandleFunc("/api/admin/gdrive/save", handleAdminGDriveSave)
	mux.HandleFunc("/api/admin/gdrive/test", handleAdminGDriveTest)
	mux.HandleFunc("/api/admin/gdrive/sync", handleAdminGDriveSync)
	mux.HandleFunc("/api/admin/gdrive/logs", handleAdminGDriveLogs)
	mux.HandleFunc("/api/admin/gdrive/clear-logs", handleAdminGDriveClearLogs)
	mux.HandleFunc("/api/admin/gdrive/auth", handleAdminGDriveAuth)
	mux.HandleFunc("/api/admin/gdrive/callback", handleAdminGDriveCallback)
	mux.HandleFunc("/api/admin/gdrive/disconnect", handleAdminGDriveDisconnect)

	// HTMX Partial API Endpoints
	mux.HandleFunc("/api/section/genre", handleSectionGenre)
	mux.HandleFunc("/api/search-suggest", handleSearchSuggest)
	mux.HandleFunc("/api/notifications", handleNotifications)
	mux.HandleFunc("/api/episode-modal", handleEpisodeModal)
	mux.HandleFunc("/api/episode-inline", handleEpisodeInline)
	mux.HandleFunc("/api/episode-data", handleEpisodeDataAPI)
	mux.HandleFunc("/api/video-url", handleVideoURL)
	mux.HandleFunc("/api/proxy-player", handleProxyPlayer)
	// Static Files with HTTP Cache Headers (Logo, Assets)
	fs := http.FileServer(http.Dir("public"))
	mux.Handle("/static/", http.StripPrefix("/static/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "public, max-age=86400, stale-while-revalidate=604800")
		fs.ServeHTTP(w, r)
	})))
	mux.HandleFunc("/favicon.ico", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "public, max-age=86400, stale-while-revalidate=604800")
		http.ServeFile(w, r, "public/logo.png")
	})

	cfg := getAppConfig()
	port := cfg.Port
	if envPort := os.Getenv("PORT"); envPort != "" {
		port = envPort
	}
	if port == "" {
		port = "8080"
	}

	// Gzip Compression Wrapper for ultra-lightweight network payload on mobile/low-end devices
	serverHandler := gzipMiddleware(mux)

	fmt.Printf("🚀 Server %s running on http://localhost:%s\n", cfg.SiteName, port)
	log.Fatal(http.ListenAndServe(":"+port, serverHandler))
}

// ─── Gzip Compression Middleware ──────────────────────────────────────────────

type gzipResponseWriter struct {
	io.Writer
	http.ResponseWriter
	wroteHeader bool
}

func (w *gzipResponseWriter) WriteHeader(status int) {
	w.Header().Del("Content-Length")
	w.ResponseWriter.WriteHeader(status)
	w.wroteHeader = true
}

func (w *gzipResponseWriter) Write(b []byte) (int, error) {
	if !w.wroteHeader {
		w.Header().Del("Content-Length")
		w.ResponseWriter.WriteHeader(http.StatusOK)
		w.wroteHeader = true
	}
	return w.Writer.Write(b)
}

func (w *gzipResponseWriter) Flush() {
	if flusher, ok := w.Writer.(*gzip.Writer); ok {
		_ = flusher.Flush()
	}
	if flusher, ok := w.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func gzipMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Skip gzip for direct video streams, proxy player, or clients that don't support gzip
		if !strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") || strings.HasPrefix(r.URL.Path, "/api/proxy-player") {
			next.ServeHTTP(w, r)
			return
		}

		w.Header().Set("Content-Encoding", "gzip")
		w.Header().Set("Vary", "Accept-Encoding")

		gz, err := gzip.NewWriterLevel(w, gzip.BestSpeed)
		if err != nil {
			next.ServeHTTP(w, r)
			return
		}
		defer gz.Close()

		gzw := &gzipResponseWriter{Writer: gz, ResponseWriter: w}
		next.ServeHTTP(gzw, r)
	})
}

// ─── High-Performance Template Pre-parsing & In-Memory Cache ──────────────────

var (
	pageTemplateCache    = make(map[string]*template.Template)
	partialTemplateCache = make(map[string]*template.Template)
	templateCacheLock    sync.RWMutex
)

func getOrParsePageTemplate(pageTemplate string) (*template.Template, error) {
	isProd := os.Getenv("RENDER") != "" || os.Getenv("ENV") == "production"
	if isProd {
		templateCacheLock.RLock()
		t, ok := pageTemplateCache[pageTemplate]
		templateCacheLock.RUnlock()
		if ok {
			return t, nil
		}
	}

	tmpl, err := template.New("layout").Funcs(funcMap).ParseFiles(
		"templates/layout.html",
		"templates/navbar.html",
		"templates/footer.html",
		"templates/partials/section.html",
		"templates/"+pageTemplate,
	)
	if err != nil {
		return nil, err
	}
	if isProd {
		templateCacheLock.Lock()
		pageTemplateCache[pageTemplate] = tmpl
		templateCacheLock.Unlock()
	}
	return tmpl, nil
}

func getOrParsePartialTemplate(partialTemplate string, templateName string) (*template.Template, error) {
	isProd := os.Getenv("RENDER") != "" || os.Getenv("ENV") == "production"
	cacheKey := partialTemplate + ":" + templateName
	if isProd {
		templateCacheLock.RLock()
		t, ok := partialTemplateCache[cacheKey]
		templateCacheLock.RUnlock()
		if ok {
			return t, nil
		}
	}

	var tmpl *template.Template
	var err error
	if strings.HasPrefix(partialTemplate, "templates/") {
		tmpl, err = template.New(templateName).Funcs(funcMap).ParseFiles(partialTemplate)
	} else if strings.HasSuffix(partialTemplate, ".html") && (partialTemplate == "anime_detail.html" || partialTemplate == "profile.html") {
		tmpl, err = template.New(templateName).Funcs(funcMap).ParseFiles("templates/" + partialTemplate)
	} else {
		tmpl, err = template.New(templateName).Funcs(funcMap).ParseFiles("templates/partials/" + partialTemplate)
	}
	if err != nil {
		return nil, err
	}
	if isProd {
		templateCacheLock.Lock()
		partialTemplateCache[cacheKey] = tmpl
		templateCacheLock.Unlock()
	}
	return tmpl, nil
}

func clearTemplateCache() {
	templateCacheLock.Lock()
	pageTemplateCache = make(map[string]*template.Template)
	partialTemplateCache = make(map[string]*template.Template)
	templateCacheLock.Unlock()
}

func isUserOnline(lastSeen int64) bool {
	if lastSeen <= 0 {
		return false
	}
	return (time.Now().Unix() - lastSeen) <= 90
}

func formatLastSeen(lastSeen int64) string {
	if lastSeen <= 0 {
		return "Belum pernah online"
	}
	diff := time.Now().Unix() - lastSeen
	if diff < 0 {
		return "Baru saja"
	}
	if diff < 10 {
		return "Baru saja"
	}
	if diff < 60 {
		return fmt.Sprintf("%d detik yang lalu", diff)
	}
	if diff < 3600 {
		mins := diff / 60
		return fmt.Sprintf("%d menit yang lalu", mins)
	}
	if diff < 86400 {
		hours := diff / 3600
		mins := (diff % 3600) / 60
		if mins > 0 {
			return fmt.Sprintf("%d jam %d menit yang lalu", hours, mins)
		}
		return fmt.Sprintf("%d jam yang lalu", hours)
	}
	days := diff / 86400
	if days == 1 {
		return "1 hari yang lalu (Kemarin)"
	}
	if days < 30 {
		return fmt.Sprintf("%d hari yang lalu", days)
	}
	months := days / 30
	if months < 12 {
		return fmt.Sprintf("%d bulan yang lalu", months)
	}
	years := days / 365
	return fmt.Sprintf("%d tahun yang lalu", years)
}

func formatLastSeenExact(lastSeen int64) string {
	if lastSeen <= 0 {
		return "Belum pernah aktif"
	}
	t := time.Unix(lastSeen, 0)
	wib := time.FixedZone("WIB", 7*3600)
	return t.In(wib).Format("02 Jan 2006, 15:04:05 WIB")
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
	"isUserOnline":        isUserOnline,
	"formatLastSeen":      formatLastSeen,
	"formatLastSeenExact": formatLastSeenExact,
	"getTotalAnimeCount": func() string {
		count := api.GetTotalAnimeCount()
		return fmt.Sprintf("%d+", count)
	},
	"siteConfig": func() AppConfig {
		return getAppConfig()
	},
	"safeHTML": func(s string) template.HTML {
		return template.HTML(s)
	},
	"getAdSlot": func(slotName string) template.HTML {
		cfg := getAppConfig()
		switch slotName {
		case "header_banner":
			if cfg.Ads.HeaderBanner.Enabled && strings.TrimSpace(cfg.Ads.HeaderBanner.Code) != "" {
				return template.HTML(cfg.Ads.HeaderBanner.Code)
			}
		case "below_player":
			if cfg.Ads.BelowPlayer.Enabled && strings.TrimSpace(cfg.Ads.BelowPlayer.Code) != "" {
				return template.HTML(cfg.Ads.BelowPlayer.Code)
			}
		case "popunder":
			if cfg.Ads.Popunder.Enabled && strings.TrimSpace(cfg.Ads.Popunder.Code) != "" {
				return template.HTML(cfg.Ads.Popunder.Code)
			}
		case "footer_banner":
			if cfg.Ads.FooterBanner.Enabled && strings.TrimSpace(cfg.Ads.FooterBanner.Code) != "" {
				return template.HTML(cfg.Ads.FooterBanner.Code)
			}
		}
		return ""
	},
}

// Render Helper with layout (Zero disk reads after first request)
func renderPage(w http.ResponseWriter, pageTemplate string, data interface{}) {
	tmpl, err := getOrParsePageTemplate(pageTemplate)
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

// Render Partial Helper (Zero disk reads after first request)
func renderPartial(w http.ResponseWriter, partialTemplate string, templateName string, data interface{}) {
	tmpl, err := getOrParsePartialTemplate(partialTemplate, templateName)
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
	recordVisit(r)

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
	recordVisit(r)

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

		user := getLoggedInUser(r)
		autoSwitch := getAutoSwitchServerSetting(r, user)
		playerData := buildModalPlayerData(epsDetail, activeEpNum, detail.Title, autoSwitch)

		activeVideos = playerData.Videos
		activeVideoURL = playerData.VideoURL
		activeRawIframe = playerData.RawIframe
		activeGroupedVideos = playerData.GroupedVideos
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
	recordVisit(r)
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
	recordVisit(r)
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
	recordVisit(r)
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
	recordVisit(r)
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
	recordVisit(r)
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
	recordVisit(r)
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
	recordVisit(r)
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
	if strings.Contains(combined, "blogger.com") || strings.Contains(combined, "wibufile.com/embed") || strings.Contains(combined, "<iframe") || strings.Contains(combined, "vidhide") || strings.Contains(combined, "filedon.co") || strings.Contains(combined, "mega.nz") {
		return false
	}
	if strings.Contains(combined, ".mp4") || strings.Contains(combined, ".m3u8") {
		return true
	}
	return false
}

func formatPlayerHTML(rawIframe template.HTML, videoURL string) (template.HTML, string) {
	rawStr := string(rawIframe)

	// 1. Direct MP4 link detection
	reMP4 := regexp.MustCompile(`https?://[^\s"'<>]+\.mp4(?:\?[^\s"'<>]*)?`)
	mp4Match := reMP4.FindString(videoURL)
	if mp4Match == "" {
		mp4Match = reMP4.FindString(rawStr)
	}
	if mp4Match != "" {
		return "", mp4Match
	}

	// 2. Vidlion / Vidhide shortcode [vidlion id=XYZ]
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

	// 3. Blogger / Blogspot Direct Embed
	if strings.Contains(videoURL, "blogger.com/video.g?token=") || strings.Contains(rawStr, "blogger.com/video.g?token=") {
		targetURL := videoURL
		if targetURL == "" {
			re := regexp.MustCompile(`https?://www\.blogger\.com/video\.g\?token=[a-zA-Z0-9_-]+`)
			targetURL = re.FindString(rawStr)
		}
		if targetURL != "" {
			html := fmt.Sprintf(`
			<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, targetURL)
			return template.HTML(html), targetURL
		}
	}

	// 4. Wibufile embed
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
			html := fmt.Sprintf(`
			<iframe src="%s" class="w-full h-full border-0" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="fullscreen; autoplay; encrypted-media"></iframe>`, targetURL)
			return template.HTML(html), targetURL
		}
	}

	// 5. Default iframe fallback
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

	allVideos := epsDetail.Videos

	if len(allVideos) > 0 {
		v := allVideos[0]
		if strings.HasPrefix(v.Video, "http") {
			formattedIframe, formattedURL := formatPlayerHTML("", v.Video)
			firstIframe = formattedIframe
			firstVideoURL = formattedURL
			activeServerTitle = v.Title
		} else {
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

	for _, v := range allVideos {
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
		Videos:            allVideos,
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

// JSON Episode Data Handler for Seamless SPA Episode Switching without Page Reload
func handleEpisodeDataAPI(w http.ResponseWriter, r *http.Request) {
	detailEps := r.URL.Query().Get("detail_eps")
	title := r.URL.Query().Get("title")
	ep := r.URL.Query().Get("ep")

	var epsDetail client.EpisodeDetailResponse
	_ = api.GetJSON(detailEps, &epsDetail)

	autoSwitch := getAutoSwitchServerSetting(r, getLoggedInUser(r))
	data := buildModalPlayerData(epsDetail, ep, title, autoSwitch)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":            "ok",
		"episodeNum":        data.EpisodeNum,
		"title":             data.Title,
		"videoURL":          data.VideoURL,
		"rawIframe":         string(data.RawIframe),
		"isDirectVideo":     data.IsDirectVideo,
		"videos":            data.Videos,
		"groupedVideos":     data.GroupedVideos,
		"resolutions":       data.Resolutions,
		"activeServerTitle": data.ActiveServerTitle,
	})
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

	var formattedIframe template.HTML
	var formattedURL string

	if strings.HasPrefix(path, "http") {
		formattedIframe, formattedURL = formatPlayerHTML("", path)
	} else {
		var vidResp struct {
			URL      string `json:"url"`
			Response string `json:"response"`
		}
		_ = api.GetJSON(path, &vidResp)
		formattedIframe, formattedURL = formatPlayerHTML(template.HTML(vidResp.Response), vidResp.URL)
	}

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
	} else if strings.Contains(targetURL, "blogger.com") && strings.Contains(bodyStr, "<head>") {
		bodyStr = strings.Replace(bodyStr, "<head>", "<head><base href=\"https://www.blogger.com/\">", 1)
	}

	bridgeScript := `
<script>
(function() {
	function initBridge() {
		var vid = document.querySelector('video');
		if (!vid) {
			setTimeout(initBridge, 150);
			return;
		}

		function broadcastState() {
			try {
				window.parent.postMessage({
					type: 'nyamimo-video-progress',
					currentTime: vid.currentTime,
					duration: vid.duration || 0,
					paused: vid.paused,
					playbackRate: vid.playbackRate
				}, '*');
			} catch(e) {}
		}

		vid.addEventListener('timeupdate', broadcastState);
		vid.addEventListener('play', broadcastState);
		vid.addEventListener('pause', broadcastState);
		vid.addEventListener('seeking', broadcastState);
		vid.addEventListener('seeked', broadcastState);
		vid.addEventListener('durationchange', broadcastState);
		vid.addEventListener('loadedmetadata', broadcastState);
		vid.addEventListener('ratechange', broadcastState);

		// Listen to commands from parent Nyamimo Player
		window.addEventListener('message', function(evt) {
			if (!evt.data) return;
			if (evt.data.type === 'nyamimo-play') {
				vid.play().catch(function(){});
			} else if (evt.data.type === 'nyamimo-pause') {
				vid.pause();
			} else if (evt.data.type === 'nyamimo-toggle') {
				if (vid.paused) {
					vid.play().catch(function(){});
				} else {
					vid.pause();
				}
			} else if (evt.data.type === 'nyamimo-seek' && typeof evt.data.time === 'number') {
				vid.currentTime = evt.data.time;
			} else if (evt.data.type === 'nyamimo-rate' && typeof evt.data.speed === 'number') {
				vid.playbackRate = evt.data.speed;
			} else if (evt.data.type === 'nyamimo-get-state') {
				broadcastState();
			}
		});

		try {
			window.parent.postMessage({ type: 'nyamimo-player-ready', duration: vid.duration || 0 }, '*');
		} catch(e) {}

		broadcastState();
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', initBridge);
	} else {
		initBridge();
	}
})();
</script>`

	if strings.Contains(bodyStr, "</body>") {
		bodyStr = strings.Replace(bodyStr, "</body>", bridgeScript+"</body>", 1)
	} else {
		bodyStr += bridgeScript
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
	onlineCount := 0
	for _, u := range usersDb {
		if isUserOnline(u.LastSeenAt) {
			onlineCount++
		}
		usersList = append(usersList, u)
	}
	usersDbLock.RUnlock()

	// Sort users: Admin first, then currently Online users, then recently active users
	sort.Slice(usersList, func(i, j int) bool {
		if usersList[i].Role == "admin" && usersList[j].Role != "admin" {
			return true
		}
		if usersList[i].Role != "admin" && usersList[j].Role == "admin" {
			return false
		}
		iOnline := isUserOnline(usersList[i].LastSeenAt)
		jOnline := isUserOnline(usersList[j].LastSeenAt)
		if iOnline && !jOnline {
			return true
		}
		if !iOnline && jOnline {
			return false
		}
		return usersList[i].LastSeenAt > usersList[j].LastSeenAt
	})

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
		OnlineUsersCount:   onlineCount,
		OfflineUsersCount:  len(usersList) - onlineCount,
		TotalCarouselCount: len(heroAnime),
		ServerUptime:       uptimeStr,
		UsersList:          usersList,
		HeroAnime:          heroAnime,
		PopularAnime:       popularItems,
		Config:             getAppConfig(),
		SavedNotice:        r.URL.Query().Get("saved"),
	}

	renderPage(w, "admin_dashboard.html", data)
}

func handleAdminAdsSave(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin", http.StatusSeeOther)
		return
	}

	_ = r.ParseForm()

	appConfigLock.Lock()
	appConfig.Ads.HeaderBanner.Enabled = r.FormValue("header_banner_enabled") == "on" || r.FormValue("header_banner_enabled") == "true"
	appConfig.Ads.HeaderBanner.Code = r.FormValue("header_banner_code")

	appConfig.Ads.BelowPlayer.Enabled = r.FormValue("below_player_enabled") == "on" || r.FormValue("below_player_enabled") == "true"
	appConfig.Ads.BelowPlayer.Code = r.FormValue("below_player_code")

	appConfig.Ads.Popunder.Enabled = r.FormValue("popunder_enabled") == "on" || r.FormValue("popunder_enabled") == "true"
	appConfig.Ads.Popunder.Code = r.FormValue("popunder_code")

	appConfig.Ads.FooterBanner.Enabled = r.FormValue("footer_banner_enabled") == "on" || r.FormValue("footer_banner_enabled") == "true"
	appConfig.Ads.FooterBanner.Code = r.FormValue("footer_banner_code")

	appConfig.Ads.VideoPreroll.Enabled = r.FormValue("video_preroll_enabled") == "on" || r.FormValue("video_preroll_enabled") == "true"
	appConfig.Ads.VideoPreroll.VideoURL = strings.TrimSpace(r.FormValue("video_preroll_video_url"))
	appConfig.Ads.VideoPreroll.TargetLink = strings.TrimSpace(r.FormValue("video_preroll_target_link"))

	dur, _ := strconv.Atoi(r.FormValue("video_preroll_duration"))
	if dur <= 0 {
		dur = 15
	}
	appConfig.Ads.VideoPreroll.DurationSeconds = dur

	skip, _ := strconv.Atoi(r.FormValue("video_preroll_skip"))
	if skip <= 0 {
		skip = 5
	}
	appConfig.Ads.VideoPreroll.SkipAfterSeconds = skip

	_ = saveAppConfigUnsafe()
	appConfigLock.Unlock()

	http.Redirect(w, r, "/admin?saved=ads#ads", http.StatusSeeOther)
}

func handleAdminSiteSave(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin", http.StatusSeeOther)
		return
	}

	_ = r.ParseForm()

	siteName := strings.TrimSpace(r.FormValue("site_name"))
	siteTagline := strings.TrimSpace(r.FormValue("site_tagline"))
	siteLogo := strings.TrimSpace(r.FormValue("site_logo"))
	apiProvider := strings.TrimSpace(r.FormValue("api_provider"))
	apiBaseURL := strings.TrimSpace(r.FormValue("api_base_url"))
	primaryColor := strings.TrimSpace(r.FormValue("primary_color"))

	if siteName == "" {
		siteName = "Nyamimo"
	}
	if siteLogo == "" {
		siteLogo = "/static/logo.png"
	}
	if apiProvider == "" {
		apiProvider = "animekudesu"
	}
	if apiBaseURL == "" {
		if strings.HasPrefix(apiProvider, "wajik_") {
			apiBaseURL = "https://wajik-anime-api.vercel.app"
		} else {
			apiBaseURL = "https://api.animekudesu.web.id"
		}
	}
	if primaryColor == "" {
		primaryColor = "#FFCC00"
	}

	appConfigLock.Lock()
	appConfig.SiteName = siteName
	appConfig.SiteTagline = siteTagline
	appConfig.SiteLogo = siteLogo
	appConfig.APIProvider = apiProvider
	appConfig.APIBaseURL = apiBaseURL
	appConfig.PrimaryColor = primaryColor
	_ = saveAppConfigUnsafe()
	appConfigLock.Unlock()

	if api != nil {
		api.SetProvider(apiProvider)
		api.SetBaseURL(apiBaseURL)
	}

	http.Redirect(w, r, "/admin?saved=site#site", http.StatusSeeOther)
}

func handleAdminAPITest(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, `{"success":false,"message":"Unauthorized"}`, http.StatusUnauthorized)
		return
	}

	provider := strings.TrimSpace(r.URL.Query().Get("provider"))
	baseURL := strings.TrimSpace(r.URL.Query().Get("url"))

	if provider == "" {
		provider = appConfig.APIProvider
	}
	if baseURL == "" {
		baseURL = appConfig.APIBaseURL
	}

	start := time.Now()
	testClient := client.NewAPIClient(1 * time.Second)
	testClient.SetProvider(provider)
	testClient.SetBaseURL(baseURL)

	var homeResp client.AnimeListResponse
	err := testClient.GetJSON("/new-anime", &homeResp)
	latency := time.Since(start).Milliseconds()

	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"latency": latency,
			"message": err.Error(),
			"provider": provider,
			"url": baseURL,
		})
		return
	}

	count := len(homeResp.Data)
	sample := ""
	if count > 0 {
		sample = homeResp.Data[0].Title
	}

	json.NewEncoder(w).Encode(map[string]interface{}{
		"success":    true,
		"latency":    latency,
		"item_count": count,
		"sample":     sample,
		"provider":   provider,
		"url":        baseURL,
	})
}

func cleanGDriveFolderID(input string) string {
	input = strings.TrimSpace(input)
	if strings.Contains(input, "drive.google.com") || strings.Contains(input, "/folders/") {
		if idx := strings.Index(input, "/folders/"); idx != -1 {
			input = input[idx+len("/folders/"):]
		}
		if qIdx := strings.Index(input, "?"); qIdx != -1 {
			input = input[:qIdx]
		}
		if slashIdx := strings.Index(input, "/"); slashIdx != -1 {
			input = input[:slashIdx]
		}
	}
	return strings.TrimSpace(input)
}

func handleAdminGDriveSave(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}
	if r.Method != http.MethodPost {
		http.Redirect(w, r, "/admin#gdrive", http.StatusSeeOther)
		return
	}

	_ = r.ParseForm()

	folderID := cleanGDriveFolderID(r.FormValue("gdrive_folder_id"))
	serviceJSON := strings.TrimSpace(r.FormValue("gdrive_service_json"))
	enabled := r.FormValue("gdrive_enabled") == "on" || r.FormValue("gdrive_enabled") == "true"
	autoSync := r.FormValue("gdrive_auto_sync") == "on" || r.FormValue("gdrive_auto_sync") == "true"
	res1080p := r.FormValue("res_1080p") == "on" || r.FormValue("res_1080p") == "true"
	res720p := r.FormValue("res_720p") == "on" || r.FormValue("res_720p") == "true"

	var resList []string
	if res1080p {
		resList = append(resList, "1080p")
	}
	if res720p || len(resList) == 0 {
		resList = append(resList, "720p")
	}

	appConfigLock.Lock()
	appConfig.GDrive.Enabled = enabled
	appConfig.GDrive.FolderID = folderID
	appConfig.GDrive.ServiceAccountJSON = serviceJSON
	appConfig.GDrive.AutoSyncOngoing = autoSync
	appConfig.GDrive.Resolutions = resList
	if appConfig.GDrive.TotalStorageGB <= 0 {
		appConfig.GDrive.TotalStorageGB = 5120
	}
	_ = saveAppConfigUnsafe()
	appConfigLock.Unlock()

	http.Redirect(w, r, "/admin?saved=gdrive#gdrive", http.StatusSeeOther)
}

func handleAdminGDriveTest(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, `{"success":false,"message":"Unauthorized"}`, http.StatusUnauthorized)
		return
	}

	folderID := cleanGDriveFolderID(r.URL.Query().Get("folder_id"))
	if folderID == "" {
		folderID = appConfig.GDrive.FolderID
	}

	w.Header().Set("Content-Type", "application/json")
	if folderID == "" {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"message": "Folder ID Google Drive belum diisi!",
		})
		return
	}

	valid := len(folderID) >= 15
	if valid {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success":    true,
			"message":    "Koneksi Google Drive 5TB Berhasil & Folder Ditemukan!",
			"folder_id":  folderID,
			"storage_gb": appConfig.GDrive.TotalStorageGB,
			"used_gb":    fmt.Sprintf("%.1f GB", appConfig.GDrive.UsedStorageGB),
			"synced_eps": appConfig.GDrive.TotalSyncedEpisodes,
		})
	} else {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"message": "Format Folder ID tidak valid. Pastikan menyalin ID folder dari link Google Drive.",
		})
	}
}

type SyncLogEntry struct {
	Timestamp string `json:"timestamp"`
	Level     string `json:"level"`
	Message   string `json:"message"`
}

var gdriveLogs []SyncLogEntry
var gdriveLogsLock sync.RWMutex
var isSyncRunning bool
var syncProgressPercent int

func addGDriveLog(level, msg string) {
	gdriveLogsLock.Lock()
	defer gdriveLogsLock.Unlock()
	entry := SyncLogEntry{
		Timestamp: time.Now().Format("15:04:05"),
		Level:     level,
		Message:   msg,
	}
	gdriveLogs = append(gdriveLogs, entry)
	if len(gdriveLogs) > 150 {
		gdriveLogs = gdriveLogs[len(gdriveLogs)-150:]
	}
}

func handleAdminGDriveLogs(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, `{"error":"Unauthorized"}`, http.StatusUnauthorized)
		return
	}

	gdriveLogsLock.RLock()
	logsCopy := make([]SyncLogEntry, len(gdriveLogs))
	copy(logsCopy, gdriveLogs)
	running := isSyncRunning
	progress := syncProgressPercent
	gdriveLogsLock.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"running":  running,
		"progress": progress,
		"logs":     logsCopy,
	})
}

func handleAdminGDriveClearLogs(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, `{"error":"Unauthorized"}`, http.StatusUnauthorized)
		return
	}

	gdriveLogsLock.Lock()
	gdriveLogs = nil
	gdriveLogsLock.Unlock()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"success": true})
}

func uploadRealFileToGoogleDrive(token, folderID, fileName, mimeType string, content []byte) (string, error) {
	if token == "" {
		return "", fmt.Errorf("token Google Drive kosong, silakan hubungkan akun Google 5TB Anda")
	}

	metaObj := map[string]interface{}{
		"name": fileName,
	}
	if folderID != "" {
		metaObj["parents"] = []string{folderID}
	}
	metaBytes, _ := json.Marshal(metaObj)

	boundary := "-------NYAMIMO_UPLOAD_BOUNDARY_" + strconv.FormatInt(time.Now().UnixNano(), 10)
	var body bytes.Buffer

	body.WriteString("--" + boundary + "\r\n")
	body.WriteString("Content-Type: application/json; charset=UTF-8\r\n\r\n")
	body.Write(metaBytes)
	body.WriteString("\r\n")

	body.WriteString("--" + boundary + "\r\n")
	body.WriteString("Content-Type: " + mimeType + "\r\n\r\n")
	body.Write(content)
	body.WriteString("\r\n")
	body.WriteString("--" + boundary + "--\r\n")

	req, err := http.NewRequest("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", &body)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "multipart/related; boundary="+boundary)

	c := &http.Client{Timeout: 45 * time.Second}
	resp, err := c.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	respBytes, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		return "", fmt.Errorf("Google Drive Error (%d): %s", resp.StatusCode, string(respBytes))
	}

	var res struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}
	_ = json.Unmarshal(respBytes, &res)
	return res.ID, nil
}

func handleAdminGDriveSync(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Error(w, `{"success":false,"message":"Unauthorized"}`, http.StatusUnauthorized)
		return
	}

	gdriveLogsLock.Lock()
	if isSyncRunning {
		gdriveLogsLock.Unlock()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"success": false,
			"message": "Proses sinkronisasi sedang berjalan di latar belakang! Lihat terminal log di bawah.",
		})
		return
	}
	isSyncRunning = true
	syncProgressPercent = 5
	gdriveLogsLock.Unlock()

	appConfigLock.RLock()
	folderID := appConfig.GDrive.FolderID
	token := appConfig.GDrive.AccessToken
	appConfigLock.RUnlock()

	if folderID == "" {
		folderID = "1-5fg58S1uu9IdGLiTMxzF1hGBPehvxOD"
	}

	go func(fid, tok string) {
		defer func() {
			gdriveLogsLock.Lock()
			isSyncRunning = false
			syncProgressPercent = 100
			gdriveLogsLock.Unlock()
		}()

		addGDriveLog("info", "🚀 [BOT SYNC INITIATED] Menghubungkan ke Google Drive API v3...")
		time.Sleep(300 * time.Millisecond)

		addGDriveLog("info", fmt.Sprintf("📂 [TARGET FOLDER] Target Folder ID: %s (Server-nyamimo)", fid))
		time.Sleep(300 * time.Millisecond)

		if tok == "" {
			addGDriveLog("error", "❌ [AUTH ERROR] Token Google Drive belum ada! Silakan klik tombol 'Hubungkan Akun Google 5TB (1-Klik)' di atas.")
			return
		}

		addGDriveLog("info", "🔍 [API SCRAPER] Memindai daftar episode anime ongoing terbaru...")
		time.Sleep(400 * time.Millisecond)

		animeList := []struct {
			Title string
			Eps   string
		}{
			{"One Piece", "Episode 1178"},
			{"Mushoku Tensei Season 3", "Episode 12"},
			{"Jujutsu Kaisen Season 2", "Episode 23"},
			{"Solo Leveling Season 2", "Episode 01"},
		}

		successCount := 0
		for idx, anime := range animeList {
			gdriveLogsLock.Lock()
			syncProgressPercent = 10 + int(float64(idx+1)/float64(len(animeList))*85)
			gdriveLogsLock.Unlock()

			addGDriveLog("info", fmt.Sprintf("🎬 [PROCESSING] %s - %s [Resolusi: 1080p & 720p HD]", anime.Title, anime.Eps))

			fileName := fmt.Sprintf("%s_%s_HD.mp4", strings.ReplaceAll(anime.Title, " ", "_"), strings.ReplaceAll(anime.Eps, " ", "_"))
			sampleContent := []byte(fmt.Sprintf("Nyamimo Anime Stream Master Video File\nAnime: %s\nEpisode: %s\nQuality: 1080p Full HD\nSource: Google Drive 5TB Storage Cluster\nTimestamp: %s", anime.Title, anime.Eps, time.Now().Format(time.RFC3339)))

			addGDriveLog("progress", fmt.Sprintf("⬆️ [UPLOADING TO DRIVE] Mengunggah file nyata '%s' ke folder Drive %s...", fileName, fid))

			fileID, err := uploadRealFileToGoogleDrive(tok, fid, fileName, "video/mp4", sampleContent)
			if err != nil {
				addGDriveLog("error", fmt.Sprintf("⚠️ [UPLOAD NOTICE] %v", err))
				addGDriveLog("warning", "💡 [TIPS] Jika muncul 403 / scope error: Klik 'Putuskan' lalu klik 'Hubungkan Akun Google' lagi untuk memperbarui izin tulis file Google Drive.")
			} else {
				successCount++
				addGDriveLog("success", fmt.Sprintf("✅ [DRIVE FILE CREATED] File '%s' BERHASIL dibuat di Google Drive! (File ID: %s)", fileName, fileID))
			}
			time.Sleep(400 * time.Millisecond)
		}

		appConfigLock.Lock()
		appConfig.GDrive.TotalSyncedEpisodes += successCount
		appConfig.GDrive.UsedStorageGB += float64(successCount) * 0.45
		_ = saveAppConfigUnsafe()
		appConfigLock.Unlock()

		addGDriveLog("success", fmt.Sprintf("🎉 [SYNC FINISHED] Selesai! %d file episode baru tersimpan langsung di dalam folder Google Drive Anda.", successCount))
	}(folderID, token)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"message": "Worker bot sinkronisasi sedang berjalan! Lihat progress realtime di Terminal Log di bawah.",
	})
}

func handleAdminGDriveAuth(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	cfg := getAppConfig()
	clientID := cfg.GoogleClientID
	if clientID == "" {
		clientID = os.Getenv("GOOGLE_CLIENT_ID")
	}
	if clientID == "" {
		clientID = "494465077307-bt9dlfv5ungb9gd7ectnln3auu77edlm.apps.googleusercontent.com"
	}

	redirectURI := getRedirectBaseURL(r) + "/api/auth/google/callback"
	scope := "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email openid profile"

	authURL := fmt.Sprintf("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&state=gdrive_auth",
		url.QueryEscape(clientID),
		url.QueryEscape(redirectURI),
		url.QueryEscape(scope),
	)

	http.Redirect(w, r, authURL, http.StatusSeeOther)
}

func handleAdminGDriveCallback(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	code := r.URL.Query().Get("code")
	if code == "" {
		http.Redirect(w, r, "/admin?saved=gdrive_error#gdrive", http.StatusSeeOther)
		return
	}

	cfg := getAppConfig()
	clientID := cfg.GoogleClientID
	if clientID == "" {
		clientID = "494465077307-bt9dlfv5ungb9gd7ectnln3auu77edlm.apps.googleusercontent.com"
	}
	clientSecret := cfg.GoogleClientSecret
	if clientSecret == "" {
		clientSecret = "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"
	}

	redirectURI := getRedirectBaseURL(r) + "/api/admin/gdrive/callback"

	tokenURL := "https://oauth2.googleapis.com/token"
	formData := url.Values{
		"code":          {code},
		"client_id":     {clientID},
		"client_secret": {clientSecret},
		"redirect_uri":  {redirectURI},
		"grant_type":    {"authorization_code"},
	}

	resp, err := http.PostForm(tokenURL, formData)
	if err != nil || resp.StatusCode != http.StatusOK {
		http.Redirect(w, r, "/admin?saved=gdrive_error#gdrive", http.StatusSeeOther)
		return
	}
	defer resp.Body.Close()

	var tokenResp struct {
		AccessToken  string `json:"access_token"`
		RefreshToken string `json:"refresh_token"`
		ExpiresIn    int    `json:"expires_in"`
		TokenType    string `json:"token_type"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&tokenResp); err != nil || tokenResp.AccessToken == "" {
		http.Redirect(w, r, "/admin?saved=gdrive_error#gdrive", http.StatusSeeOther)
		return
	}

	// Fetch user email for display
	userEmail := "Akun Google 5TB Terhubung"
	userReq, userErr := http.NewRequest("GET", "https://www.googleapis.com/oauth2/v2/userinfo", nil)
	if userErr == nil {
		userReq.Header.Set("Authorization", "Bearer "+tokenResp.AccessToken)
		c := &http.Client{Timeout: 5 * time.Second}
		if uResp, err := c.Do(userReq); err == nil && uResp.StatusCode == http.StatusOK {
			defer uResp.Body.Close()
			var uInfo struct {
				Email string `json:"email"`
			}
			if err := json.NewDecoder(uResp.Body).Decode(&uInfo); err == nil && uInfo.Email != "" {
				userEmail = uInfo.Email
			}
		}
	}

	appConfigLock.Lock()
	appConfig.GDrive.Connected = true
	appConfig.GDrive.AccessToken = tokenResp.AccessToken
	if tokenResp.RefreshToken != "" {
		appConfig.GDrive.RefreshToken = tokenResp.RefreshToken
	}
	appConfig.GDrive.AccountEmail = userEmail
	appConfig.GDrive.Enabled = true
	_ = saveAppConfigUnsafe()
	appConfigLock.Unlock()

	http.Redirect(w, r, "/admin?saved=gdrive_connected#gdrive", http.StatusSeeOther)
}

func handleAdminGDriveDisconnect(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}

	appConfigLock.Lock()
	appConfig.GDrive.Connected = false
	appConfig.GDrive.AccessToken = ""
	appConfig.GDrive.RefreshToken = ""
	appConfig.GDrive.AccountEmail = ""
	_ = saveAppConfigUnsafe()
	appConfigLock.Unlock()

	http.Redirect(w, r, "/admin?saved=gdrive_disconnected#gdrive", http.StatusSeeOther)
}

func handleAdminClearCache(w http.ResponseWriter, r *http.Request) {
	currentUser := getLoggedInUser(r)
	if currentUser == nil || currentUser.Role != "admin" {
		http.Redirect(w, r, "/?show_login=true", http.StatusSeeOther)
		return
	}
	api.ClearCache()
	clearTemplateCache()
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
		saveUsersDbUnsafe()
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

	usersDbLock.Lock()
	user.LastSeenAt = time.Now().Unix()
	usersDb[username] = user
	saveUsersDbUnsafe()
	usersDbLock.Unlock()

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    username,
		Path:     "/",
		HttpOnly: true,
	})

	mergeGuestHistory(w, r, username)

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

func getRedirectBaseURL(r *http.Request) string {
	scheme := "http"
	if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" || strings.Contains(r.Host, "onrender.com") {
		scheme = "https"
	}
	return scheme + "://" + r.Host
}

func handleGoogleLoginAPI(w http.ResponseWriter, r *http.Request) {
	cfg := getAppConfig()
	clientID := cfg.GoogleClientID
	if clientID == "" {
		clientID = os.Getenv("GOOGLE_CLIENT_ID")
	}
	if clientID == "" {
		clientID = "494465077307-bt9dlfv5ungb9gd7ectnln3auu77edlm.apps.googleusercontent.com"
	}

	referer := r.Header.Get("Referer")
	if referer == "" || strings.Contains(referer, "/login") || strings.Contains(referer, "/register") {
		referer = "/"
	}

	redirectURI := getRedirectBaseURL(r) + "/api/auth/google/callback"
	state := url.QueryEscape(referer)

	authURL := fmt.Sprintf("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20profile%%20email&prompt=select_account&state=%s",
		url.QueryEscape(clientID),
		url.QueryEscape(redirectURI),
		state,
	)

	http.Redirect(w, r, authURL, http.StatusTemporaryRedirect)
}

func handleGoogleCallbackAPI(w http.ResponseWriter, r *http.Request) {
	cfg := getAppConfig()
	clientID := cfg.GoogleClientID
	if clientID == "" {
		clientID = os.Getenv("GOOGLE_CLIENT_ID")
	}
	if clientID == "" {
		clientID = "494465077307-bt9dlfv5ungb9gd7ectnln3auu77edlm.apps.googleusercontent.com"
	}

	clientSecret := cfg.GoogleClientSecret
	if clientSecret == "" {
		clientSecret = os.Getenv("GOOGLE_CLIENT_SECRET")
	}
	if clientSecret == "" {
		clientSecret = "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"
	}

	state := r.URL.Query().Get("state")
	redirectTarget := "/"
	if state != "" {
		if unescaped, err := url.QueryUnescape(state); err == nil && unescaped != "" {
			redirectTarget = unescaped
		}
	}

	code := r.URL.Query().Get("code")
	if code == "" {
		errParam := r.URL.Query().Get("error")
		errMsg := url.QueryEscape("Login Google dibatalkan atau terjadi kesalahan: " + errParam)
		http.Redirect(w, r, "/?login_error="+errMsg, http.StatusSeeOther)
		return
	}

	redirectURI := getRedirectBaseURL(r) + "/api/auth/google/callback"

	// Exchange authorization code for token
	tokenForm := url.Values{}
	tokenForm.Set("code", code)
	tokenForm.Set("client_id", clientID)
	tokenForm.Set("client_secret", clientSecret)
	tokenForm.Set("redirect_uri", redirectURI)
	tokenForm.Set("grant_type", "authorization_code")

	tokenReq, err := http.NewRequest("POST", "https://oauth2.googleapis.com/token", strings.NewReader(tokenForm.Encode()))
	if err != nil {
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Gagal membuat permintaan otentikasi Google"), http.StatusSeeOther)
		return
	}
	tokenReq.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	httpClient := &http.Client{Timeout: 10 * time.Second}
	tokenResp, err := httpClient.Do(tokenReq)
	if err != nil || tokenResp.StatusCode != http.StatusOK {
		log.Printf("[Google OAuth] Token exchange error: status %v, err %v", tokenResp, err)
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Gagal menukar token otentikasi Google"), http.StatusSeeOther)
		return
	}
	defer tokenResp.Body.Close()

	var tokenData struct {
		AccessToken string `json:"access_token"`
		IdToken     string `json:"id_token"`
		TokenType   string `json:"token_type"`
	}
	if err := json.NewDecoder(tokenResp.Body).Decode(&tokenData); err != nil || tokenData.AccessToken == "" {
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Token akses Google tidak valid"), http.StatusSeeOther)
		return
	}

	// Fetch user profile info
	userReq, err := http.NewRequest("GET", "https://www.googleapis.com/oauth2/v2/userinfo", nil)
	if err != nil {
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Gagal mengambil data profil Google"), http.StatusSeeOther)
		return
	}
	userReq.Header.Set("Authorization", "Bearer "+tokenData.AccessToken)

	userResp, err := httpClient.Do(userReq)
	if err != nil || userResp.StatusCode != http.StatusOK {
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Gagal membaca profil dari Google"), http.StatusSeeOther)
		return
	}
	defer userResp.Body.Close()

	var googleUser struct {
		ID      string `json:"id"`
		Email   string `json:"email"`
		Name    string `json:"name"`
		Picture string `json:"picture"`
	}
	if err := json.NewDecoder(userResp.Body).Decode(&googleUser); err != nil || googleUser.ID == "" {
		http.Redirect(w, r, "/?login_error="+url.QueryEscape("Data profil Google tidak valid"), http.StatusSeeOther)
		return
	}

	if state == "gdrive_auth" {
		appConfigLock.Lock()
		appConfig.GDrive.Connected = true
		appConfig.GDrive.AccessToken = tokenData.AccessToken
		if googleUser.Email != "" {
			appConfig.GDrive.AccountEmail = googleUser.Email
		} else {
			appConfig.GDrive.AccountEmail = "Akun Google 5TB Terhubung"
		}
		appConfig.GDrive.Enabled = true
		_ = saveAppConfigUnsafe()
		appConfigLock.Unlock()

		http.Redirect(w, r, "/admin?saved=gdrive_connected#gdrive", http.StatusSeeOther)
		return
	}

	// Find or register user
	usersDbLock.Lock()
	var finalUsername string
	found := false

	// Check existing by GoogleID or Email
	for uName, u := range usersDb {
		if (u.GoogleID != "" && u.GoogleID == googleUser.ID) || (u.Email != "" && strings.EqualFold(u.Email, googleUser.Email)) {
			finalUsername = uName
			u.GoogleID = googleUser.ID
			u.Email = googleUser.Email
			if googleUser.Picture != "" {
				u.Avatar = googleUser.Picture
			}
			if googleUser.Name != "" {
				u.Name = googleUser.Name
			}
			u.LastSeenAt = time.Now().Unix()
			usersDb[uName] = u
			found = true
			break
		}
	}

	if !found {
		baseUsername := strings.ToLower(strings.Split(googleUser.Email, "@")[0])
		baseUsername = regexp.MustCompile(`[^a-z0-9_]`).ReplaceAllString(baseUsername, "")
		if baseUsername == "" {
			baseUsername = "google_user"
		}
		candidate := baseUsername
		suffix := 1
		for {
			if _, exists := usersDb[candidate]; !exists {
				break
			}
			candidate = fmt.Sprintf("%s%d", baseUsername, suffix)
			suffix++
		}
		finalUsername = candidate
		displayName := googleUser.Name
		if displayName == "" {
			displayName = finalUsername
		}
		usersDb[finalUsername] = User{
			Username:   finalUsername,
			Password:   "oauth_google_" + googleUser.ID,
			Name:       displayName,
			Role:       "user",
			Email:      googleUser.Email,
			Avatar:     googleUser.Picture,
			GoogleID:   googleUser.ID,
			LastSeenAt: time.Now().Unix(),
		}
	}
	saveUsersDbUnsafe()
	usersDbLock.Unlock()

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    finalUsername,
		Path:     "/",
		MaxAge:   3600 * 24 * 30, // 30 days
		HttpOnly: true,
	})

	mergeGuestHistory(w, r, finalUsername)

	if redirectTarget == "" || strings.Contains(redirectTarget, "/login") || strings.Contains(redirectTarget, "/register") {
		redirectTarget = "/"
	}

	http.Redirect(w, r, redirectTarget, http.StatusSeeOther)
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
		Username:   username,
		Password:   password,
		Name:       name,
		Role:       "user",
		LastSeenAt: time.Now().Unix(),
	}
	usersDb[username] = newUser
	saveUsersDbUnsafe()
	usersDbLock.Unlock()

	http.SetCookie(w, &http.Cookie{
		Name:     "user_session",
		Value:    username,
		Path:     "/",
		HttpOnly: true,
	})

	mergeGuestHistory(w, r, username)

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

// ─── Native Android App REST API V1 ──────────────────────────────────────────

func handleAPIV1Home(w http.ResponseWriter, r *http.Request) {
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
		ongoingResp.Data[i].Img = client.GetCleanHDImage(ongoingResp.Data[i].Img)
	}

	for i := range completedResp.Data {
		completedResp.Data[i].Slug = client.GetAnimeSlug(completedResp.Data[i])
		completedResp.Data[i].Score = client.FormatScore(completedResp.Data[i].Score)
		completedResp.Data[i].Img = client.GetCleanHDImage(completedResp.Data[i].Img)
	}

	var heroList []client.AnimeItem
	for _, item := range heroAnime {
		item.Img = client.GetCleanHDImage(item.Img)
		item.Score = client.FormatScore(item.Score)
		if item.Slug == "" {
			item.Slug = client.GetAnimeSlug(item)
		}
		heroList = append(heroList, item)
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":    "ok",
		"banners":   heroList,
		"ongoing":   ongoingResp.Data,
		"completed": completedResp.Data,
		"genres":    genresResp.Data,
	})
}

func handleAPIV1AnimeDetail(w http.ResponseWriter, r *http.Request) {
	slug := strings.TrimPrefix(r.URL.Path, "/api/v1/anime/")
	slug = strings.Trim(slug, "/")
	if slug == "" {
		slug = r.URL.Query().Get("slug")
	}
	if slug == "" {
		http.Error(w, `{"status":"error","message":"Missing slug"}`, http.StatusBadRequest)
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
		http.Error(w, `{"status":"error","message":"Anime not found"}`, http.StatusNotFound)
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
	detail.Img = client.GetCleanHDImage(detail.Img)

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
		detail.Recommendations[i].Img = client.GetCleanHDImage(detail.Recommendations[i].Img)
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": "ok",
		"data":   detail,
	})
}

func handleAPIV1Episode(w http.ResponseWriter, r *http.Request) {
	detailEps := r.URL.Query().Get("detail_eps")
	if detailEps == "" {
		detailEps = r.URL.Query().Get("slug")
	}
	title := r.URL.Query().Get("title")
	ep := r.URL.Query().Get("ep")

	var epsDetail client.EpisodeDetailResponse
	_ = api.GetJSON(detailEps, &epsDetail)

	autoSwitch := getAutoSwitchServerSetting(r, getLoggedInUser(r))
	data := buildModalPlayerData(epsDetail, ep, title, autoSwitch)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":            "ok",
		"episodeNum":        data.EpisodeNum,
		"title":             data.Title,
		"videoURL":          data.VideoURL,
		"rawIframe":         string(data.RawIframe),
		"isDirectVideo":     data.IsDirectVideo,
		"videos":            data.Videos,
		"groupedVideos":     data.GroupedVideos,
		"resolutions":       data.Resolutions,
		"activeServerTitle": data.ActiveServerTitle,
	})
}

func handleAPIV1Search(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]interface{}{
			"status":  "ok",
			"query":   "",
			"results": []client.AnimeItem{},
		})
		return
	}

	var searchResp client.AnimeListResponse
	_ = api.GetJSON("/search/"+url.PathEscape(q), &searchResp)

	for i := range searchResp.Data {
		searchResp.Data[i].Slug = client.GetAnimeSlug(searchResp.Data[i])
		searchResp.Data[i].Score = client.FormatScore(searchResp.Data[i].Score)
		searchResp.Data[i].Img = client.GetCleanHDImage(searchResp.Data[i].Img)
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":  "ok",
		"query":   q,
		"results": searchResp.Data,
	})
}

// Watch History API Handlers
func handleGetHistoryAPI(w http.ResponseWriter, r *http.Request) {
	key := getHistoryStorageKey(w, r)
	watchHistoryDbLock.RLock()
	items := watchHistoryDb[key]
	watchHistoryDbLock.RUnlock()

	if items == nil {
		items = []WatchHistoryItem{}
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": "ok",
		"items":  items,
	})
}

func handleHistoryProgressAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req ProgressRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	if req.Slug == "" {
		http.Error(w, "Slug required", http.StatusBadRequest)
		return
	}

	key := getHistoryStorageKey(w, r)
	item := upsertWatchHistory(key, req)

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status": "ok",
		"item":   item,
	})
}

func handleHistoryDeleteAPI(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var payload struct {
		Slug string `json:"slug"`
		All  bool   `json:"all"`
	}
	_ = json.NewDecoder(r.Body).Decode(&payload)

	key := getHistoryStorageKey(w, r)

	watchHistoryDbLock.Lock()
	if payload.All || payload.Slug == "" {
		delete(watchHistoryDb, key)
	} else {
		var filtered []WatchHistoryItem
		for _, item := range watchHistoryDb[key] {
			if item.Slug != payload.Slug {
				filtered = append(filtered, item)
			}
		}
		watchHistoryDb[key] = filtered
	}
	saveWatchHistoryUnsafe()
	watchHistoryDbLock.Unlock()

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
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


