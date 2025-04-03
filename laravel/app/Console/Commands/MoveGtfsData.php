<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\File;

class MoveGtfsData extends Command
{
    protected $signature = 'gtfs:move {source? : Source directory of GTFS files}';
    protected $description = 'Move GTFS data files to Laravel storage';
    
    public function handle()
    {
        $sourceDir = $this->argument('source') ?? base_path('../israel-public-transportation');
        $targetDir = storage_path('app/gtfs');
        
        if (!File::exists($sourceDir)) {
            $this->error("Source directory does not exist: $sourceDir");
            return 1;
        }
        
        // Create target directory if it doesn't exist
        if (!File::exists($targetDir)) {
            File::makeDirectory($targetDir, 0755, true);
        }
        
        $this->info("Moving GTFS files from $sourceDir to $targetDir");
        
        $gtfsFiles = [
            'agency.txt',
            'routes.txt',
            'stops.txt',
            'stop_times.txt',
            'trips.txt',
            'shapes.txt'
        ];
        
        foreach ($gtfsFiles as $file) {
            $sourcePath = "$sourceDir/$file";
            $targetPath = "$targetDir/$file";
            
            if (File::exists($sourcePath)) {
                File::copy($sourcePath, $targetPath);
                $this->info("Copied: $file");
            } else {
                $this->warn("Missing file: $file");
            }
        }
        
        $this->info('GTFS data files moved successfully.');
        return 0;
    }
}
