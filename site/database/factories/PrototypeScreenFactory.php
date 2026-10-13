<?php

namespace Database\Factories;

use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends \Illuminate\Database\Eloquent\Factories\Factory<\App\Models\PrototypeScreen>
 */
class PrototypeScreenFactory extends Factory
{
    public function definition(): array
    {
        return [
            'name' => $this->faker->words(3, true),
            'slug' => $this->faker->slug(3),
            'figma_reference' => $this->faker->optional()->filePath(),
            'description' => $this->faker->sentence(),
            'endpoint' => $this->faker->optional()->regexify('/v1\/[a-z]+\/[a-z]+'),
            'tech' => $this->faker->randomElement(['Jetpack Compose', 'Google Maps', 'Firebase', 'Room SQLite']),
            'order_column' => $this->faker->numberBetween(1, 30),
        ];
    }
}