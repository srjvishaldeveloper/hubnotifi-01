<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('integration_configs', function (Blueprint $table) {
            $table->id();
            $table->string('provider', 64);
            $table->string('label', 255)->nullable();
            $table->string('mode', 32)->default('live');
            $table->boolean('enabled')->default(false);
            $table->boolean('is_default')->default(false);
            $table->text('credentials')->nullable();
            $table->text('webhook_secret')->nullable();
            $table->json('meta_json')->nullable();
            $table->unsignedBigInteger('updated_by_admin_id')->nullable();
            $table->timestamp('last_tested_at')->nullable();
            $table->string('last_test_status', 32)->nullable();
            $table->text('last_test_message')->nullable();
            $table->timestamps();

            $table->unique(['provider', 'mode']);
        });

        Schema::create('integration_audit_logs', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('admin_user_id')->nullable();
            $table->unsignedBigInteger('integration_config_id')->nullable();
            $table->string('provider', 64);
            $table->string('action', 32);
            $table->json('diff_json')->nullable();
            $table->string('ip', 45)->nullable();
            $table->string('user_agent', 512)->nullable();
            $table->timestamp('created_at')->nullable();

            $table->index('integration_config_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('integration_audit_logs');
        Schema::dropIfExists('integration_configs');
    }
};
