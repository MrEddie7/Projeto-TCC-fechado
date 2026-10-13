<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

#[Fillable([
    'name',
    'slug',
    'figma_reference',
    'description',
    'endpoint',
    'tech',
    'order_column',
])]
class PrototypeScreen extends Model
{
    /** @use HasFactory<\Database\Factories\PrototypeScreenFactory> */
    use HasFactory;

    protected function casts(): array
    {
        return [
            'order_column' => 'integer',
        ];
    }
}