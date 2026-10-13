<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;

class VehicleSeeder extends Seeder
{
    /**
     * Frota de demonstração (12 veículos) exibida na landing page e no dashboard.
     */
    public function run(): void
    {
        $vehicles = [
            ['plate' => 'ABC1D23', 'brand' => 'Toyota', 'model' => 'Corolla Cross', 'year' => 2023, 'color' => 'Branco', 'is_blocked' => false, 'latitude' => -23.5505, 'longitude' => -46.6333, 'speed' => 62],
            ['plate' => 'BRA2E45', 'brand' => 'Chevrolet', 'model' => 'Onix Plus', 'year' => 2022, 'color' => 'Prata', 'is_blocked' => false, 'latitude' => -23.5412, 'longitude' => -46.6401, 'speed' => 45],
            ['plate' => 'SPT3F67', 'brand' => 'Volkswagen', 'model' => 'T-Cross', 'year' => 2024, 'color' => 'Vermelho', 'is_blocked' => false, 'latitude' => -23.5589, 'longitude' => -46.6210, 'speed' => 88],
            ['plate' => 'VLT4G89', 'brand' => 'Fiat', 'model' => 'Strada', 'year' => 2021, 'color' => 'Azul', 'is_blocked' => true, 'latitude' => -23.5602, 'longitude' => -46.6517, 'speed' => 0],
            ['plate' => 'RIO5H01', 'brand' => 'Hyundai', 'model' => 'HB20', 'year' => 2023, 'color' => 'Preto', 'is_blocked' => false, 'latitude' => -23.5349, 'longitude' => -46.6284, 'speed' => 37],
            ['plate' => 'CMP6I12', 'brand' => 'Ford', 'model' => 'Ranger', 'year' => 2022, 'color' => 'Verde', 'is_blocked' => false, 'latitude' => -23.5706, 'longitude' => -46.6450, 'speed' => 71],
            ['plate' => 'JDM7K34', 'brand' => 'Jeep', 'model' => 'Compass', 'year' => 2024, 'color' => 'Branco', 'is_blocked' => false, 'latitude' => -23.5188, 'longitude' => -46.6115, 'speed' => 53],
            ['plate' => 'GRU8L56', 'brand' => 'Honda', 'model' => 'Civic', 'year' => 2020, 'color' => 'Prata', 'is_blocked' => false, 'latitude' => -23.5771, 'longitude' => -46.6001, 'speed' => 29],
            ['plate' => 'BSO9M78', 'brand' => 'Volkswagen', 'model' => 'Gol', 'year' => 2019, 'color' => 'Preto', 'is_blocked' => true, 'latitude' => -23.5432, 'longitude' => -46.6668, 'speed' => 0],
            ['plate' => 'SOR0N90', 'brand' => 'Fiat', 'model' => 'Uno', 'year' => 2018, 'color' => 'Vermelho', 'is_blocked' => false, 'latitude' => -23.5350, 'longitude' => -46.6359, 'speed' => 41],
            ['plate' => 'PET2Q11', 'brand' => 'Toyota', 'model' => 'Hilux', 'year' => 2023, 'color' => 'Verde', 'is_blocked' => false, 'latitude' => -23.5650, 'longitude' => -46.6700, 'speed' => 96],
            ['plate' => 'SCC4R22', 'brand' => 'Chevrolet', 'model' => 'S10', 'year' => 2022, 'color' => 'Azul', 'is_blocked' => false, 'latitude' => -23.5200, 'longitude' => -46.5950, 'speed' => 58],
        ];

        foreach ($vehicles as $vehicle) {
            \App\Models\Vehicle::updateOrCreate(
                ['plate' => $vehicle['plate']],
                ['last_update_at' => now()->subMinutes(random_int(1, 8))] + $vehicle
            );
        }
    }
}