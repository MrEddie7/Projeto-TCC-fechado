<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

#[Fillable([
    'plate',
    'brand',
    'model',
    'year',
    'color',
    'is_blocked',
    'latitude',
    'longitude',
    'speed',
    'last_update_at',
])]
class Vehicle extends Model
{
    /** @use HasFactory<\Database\Factories\VehicleFactory> */
    use HasFactory;

    protected function casts(): array
    {
        return [
            'is_blocked' => 'boolean',
            'latitude' => 'float',
            'longitude' => 'float',
            'speed' => 'integer',
            'last_update_at' => 'datetime',
        ];
    }
}