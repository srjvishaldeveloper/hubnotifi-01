<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('channel_accounts', function (Blueprint $table) {
            $table->id();
            $table->foreignId('workspace_id')->constrained('workspaces')->onDelete('cascade');
            $table->string('channel', 32);
            $table->string('provider', 32)->default('meta');
            $table->text('credentials')->nullable();
            $table->string('display_name', 255)->nullable();
            $table->string('phone_number_id', 128)->nullable();
            $table->string('business_account_id', 128)->nullable();
            $table->string('status', 32)->default('active');
            $table->json('meta_json')->nullable();
            $table->timestamps();

            $table->index(['workspace_id', 'channel']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('channel_accounts');
    }
};
