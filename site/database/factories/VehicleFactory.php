<?php

namespace Database\Factories;

use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\Vehicle>
 */
class VehicleFactory extends Factory
{
    public function definition(): array
    {
        $letters = strtoupper($this->faker->randomElement(['ABC', 'BRA', 'SPT', 'VLT', 'RIO', 'CMP', 'JDM', 'GRU', 'BSO', 'SOR', 'PET', 'SCC']));

        return [
            'plate' => $letters.$this->faker->numberBetween(0, 9).$this->faker->randomLetter().$this->faker->numberBetween(10, 99),
            'brand' => $this->faker->randomElement(['Toyota', 'Chevrolet', 'Volkswagen', 'Fiat', 'Hyundai', 'Ford', 'Honda', 'Jeep']),
            'model' => $this->faker->randomElement(['Corolla', 'Onix', 'T-Cross', 'Strada', 'HB20', 'Ranger', 'Civic', 'Compass', 'Gol', 'Uno']),
            'year' => $this->faker->numberBetween(2018, 2025),
            'color' => $this->faker->randomElement(['Branco', 'Preto', 'Prata', 'Vermelho', 'Azul', 'Verde']),
            'is_blocked' => $this->faker->boolean(15),
            'latitude' => $this->faker->latitude(-23.6, -23.5),
            'longitude' => $this->faker->longitude(-46.7, -46.5),
            'speed' => $this->faker->numberBetween(0, 110),
            'last_update_at' => $this->faker->dateTimeBetween('-10 minutes'),
        ];
    }
}