<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('automations', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->unsignedBigInteger('workspace_id');
            $table->string('name', 255);
            $table->string('status', 32)->default('draft');
            $table->string('trigger_type', 64)->nullable();
            $table->json('trigger_config')->nullable();
            $table->string('trigger_token', 128)->nullable();
            $table->json('nodes')->nullable();
            $table->json('edges')->nullable();
            $table->unsignedInteger('run_count')->default(0);
            $table->timestamps();

            $table->index('workspace_id');
        });

        Schema::create('automation_runs', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('automation_id');
            $table->unsignedBigInteger('contact_id')->nullable();
            $table->string('status', 32)->default('running');
            $table->json('context')->nullable();
            $table->string('current_node_id', 64)->nullable();
            $table->string('resume_node_id', 64)->nullable();
            $table->text('error')->nullable();
            $table->timestamp('started_at')->nullable();
            $table->timestamp('completed_at')->nullable();
            $table->timestamps();

            $table->foreign('automation_id')->references('id')->on('automations')->cascadeOnDelete();
        });

        Schema::create('automation_run_logs', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('run_id');
            $table->string('node_id', 64)->nullable();
            $table->string('node_type', 64)->nullable();
            $table->string('result', 32)->nullable();
            $table->text('message')->nullable();
            $table->json('output')->nullable();
            $table->timestamps();

            $table->foreign('run_id')->references('id')->on('automation_runs')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('automation_run_logs');
        Schema::dropIfExists('automation_runs');
        Schema::dropIfExists('automations');
    }
};
