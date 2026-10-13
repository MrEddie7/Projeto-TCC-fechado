<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Telas/protótipos do TCC (baseado no conteúdo do Figma/Visily).
     * Cada registro descreve uma tela do app e sua referência de design.
     */
    public function up(): void
    {
        Schema::create('prototype_screens', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('slug')->unique();
            $table->string('figma_reference')->nullable();
            $table->text('description');
            $table->string('endpoint')->nullable();
            $table->string('tech')->nullable();
            $table->unsignedInteger('order_column')->default(0);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('prototype_screens');
    }
};