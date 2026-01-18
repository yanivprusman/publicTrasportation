<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Symfony\Component\Process\Process;

class TransportController extends Controller
{
    private $host = '185.159.74.218';
    private $username = 'root';
    private $apiKey = 'YP719171';
    private $baseUrl = 'http://moran.mot.gov.il:110/Channels/HTTPChannel/SmQuery/2.8/json';
    
    /**
     * Get real-time transportation data for a station
     *
     * @param Request $request
     * @return \Illuminate\Http\JsonResponse
     */
    public function getStationData(Request $request)
    {
        $station = $request->query('station', '26472');
        $line = $request->query('line');
        $detailLevel = $request->query('detail', 'calls');
        $previewInterval = $request->query('interval', 'PT30M');
        
        // Build the URL
        $url = "{$this->baseUrl}?Key={$this->apiKey}&MonitoringRef={$station}&StopVisitDetailLevel={$detailLevel}&PreviewInterval={$previewInterval}";
        
        // Add line filter if provided
        if ($line) {
            $url .= "&LineRef={$line}";
        }
        
        Log::info('Transport API Request', ['url' => $url]);
        
        try {
            // Execute the SSH command to call the API via the required host
            $command = "ssh -o StrictHostKeyChecking=no root@{$this->host} \"curl -s '{$url}'\" 2>&1";
            
            $process = Process::fromShellCommandline($command);
            $process->run();
            $output = $process->getOutput();
            
            if (empty($output)) {
                throw new \Exception("No data returned from the remote server");
            }
            
            // If output starts with SSH warnings or messages, find the JSON part
            $jsonStart = strpos($output, '{');
            if ($jsonStart !== false && $jsonStart > 0) {
                $output = substr($output, $jsonStart);
            }
            
            $decodedOutput = json_decode($output);
            
            if ($decodedOutput === null) {
                Log::error('JSON decode error', [
                    'error' => json_last_error_msg(),
                    'output' => substr($output, 0, 200)
                ]);
                
                throw new \Exception("Invalid JSON received from the remote server");
            }
            
            return response()->json($decodedOutput);
            
        } catch (\Exception $e) {
            Log::error('Transport API error', [
                'error' => $e->getMessage(),
                'station' => $station
            ]);
            
            return response()->json([
                "error" => $e->getMessage(),
                "message" => "Could not fetch data from transportation API"
            ], 500);
        }
    }
}
