<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;

class UserSeeder extends Seeder
{
    /**
     * Equipe do TCC que vai consumir este painel web.
     * role: admin (líder), aluno (membros) e coordenador (orientador).
     */
    public function run(): void
    {
        $users = [
            ['name' => 'Aluno Líder', 'email' => 'admin@tcc.com', 'role' => 'admin'],
            ['name' => 'Aluno(a) 1', 'email' => 'aluno1@tcc.com', 'role' => 'aluno'],
            ['name' => 'Aluno(a) 2', 'email' => 'aluno2@tcc.com', 'role' => 'aluno'],
            ['name' => 'Aluno(a) 3', 'email' => 'aluno3@tcc.com', 'role' => 'aluno'],
            ['name' => 'Aluno(a) 4', 'email' => 'aluno4@tcc.com', 'role' => 'aluno'],
            ['name' => 'Coordenador', 'email' => 'coordenador@tcc.com', 'role' => 'coordenador'],
        ];

        foreach ($users as $user) {
            User::updateOrCreate(
                ['email' => $user['email']],
                [
                    'name' => $user['name'],
                    'role' => $user['role'],
                    'password' => 'veiculotracker123',
                    'email_verified_at' => now(),
                ]
            );
        }
    }
}