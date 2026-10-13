<?php

use App\Http\Controllers\ProfileController;
use App\Models\PrototypeScreen;
use App\Models\User;
use App\Models\Vehicle;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Web Routes
|--------------------------------------------------------------------------
|
| Landing page e dashboard compartilham os dados do TCC (SEM CRUD ainda):
| a equipe, a frota de veículos e os protótipos das telas vêm do banco
| populado pelos seeders.
|
*/

Route::get('/', function () {
    return view('welcome', [
        'stats' => [
            'vehicles' => Vehicle::count(),
            'blockedVehicles' => Vehicle::where('is_blocked', true)->count(),
            'screens' => PrototypeScreen::count(),
            'members' => User::count(),
        ],
        'vehicles' => Vehicle::query()->orderBy('plate')->limit(12)->get(),
        'screens' => PrototypeScreen::query()->orderBy('order_column')->get(),
        'users' => User::query()->orderBy('name')->get(),
    ]);
})->name('home');

Route::get('/dashboard', function () {
    return view('dashboard', [
        'stats' => [
            'vehicles' => Vehicle::count(),
            'blockedVehicles' => Vehicle::where('is_blocked', true)->count(),
            'screens' => PrototypeScreen::count(),
            'members' => User::count(),
        ],
        'vehicles' => Vehicle::query()->orderBy('plate')->get(),
        'screens' => PrototypeScreen::query()->orderBy('order_column')->get(),
        'users' => User::query()->orderBy('name')->get(),
    ]);
})->middleware(['auth', 'verified'])->name('dashboard');

Route::middleware('auth')->group(function () {
    Route::get('/profile', [ProfileController::class, 'edit'])->name('profile.edit');
    Route::patch('/profile', [ProfileController::class, 'update'])->name('profile.update');
    Route::delete('/profile', [ProfileController::class, 'destroy'])->name('profile.destroy');
});

require __DIR__.'/auth.php';