<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class TransportService
{
    protected $apiUrl = "http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json";
    protected $apiKey = "YP719171";

    /**
     * Fetch real-time arrivals for a station
     *
     * @param string $stationCode
     * @return array
     */
    public function fetchStationArrivals($stationCode)
    {
        // Cache key for this station
        $cacheKey = "station_arrivals_{$stationCode}";

        // Check if we have fresh data in cache (1 minute TTL)
        if (Cache::has($cacheKey)) {
            return Cache::get($cacheKey);
        }

        try {
            Log::info("Fetching arrivals for station {$stationCode}");

            // Check for local proxy
            if (file_exists('/tmp/pt_proxy_port')) {
                $port = trim(file_get_contents('/tmp/pt_proxy_port'));
                $this->apiUrl = "http://localhost:{$port}/Channels/HTTPChannel/SmQuery/2.8/json";
                Log::info("Using local proxy port: {$port}");
            } else {
                throw new \Exception("Proxy not running. Run 'd publicTransportationStartProxy'.");
            }

            // Build API URL
            $url = "{$this->apiUrl}?Key={$this->apiKey}&MonitoringRef={$stationCode}";

            // Make request using Laravel's HTTP client
            $response = Http::timeout(10)->get($url);

            if (!$response->successful()) {
                Log::error("Failed to fetch arrivals: " . $response->body());
                throw new \Exception("API returned status code " . $response->status());
            }

            // Parse and validate data
            $data = $response->json();

            if (!isset($data['Siri'])) {
                throw new \Exception("Invalid data format returned from API");
            }

            // Cache data for 1 minute
            Cache::put($cacheKey, $data, now()->addMinute());

            return $data;

        } catch (\Exception $e) {
            Log::error("Error fetching arrivals: " . $e->getMessage());
            throw $e;
        }
    }
}
