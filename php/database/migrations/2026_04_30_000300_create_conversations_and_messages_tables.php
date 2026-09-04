<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('conversations', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->unsignedBigInteger('workspace_id');
            $table->unsignedBigInteger('channel_account_id')->nullable();
            $table->unsignedBigInteger('contact_id')->nullable();
            $table->string('external_thread_id', 128)->nullable();
            $table->string('status', 32)->default('open');
            $table->unsignedBigInteger('assigned_user_id')->nullable();
            $table->string('assigned_to', 64)->nullable();
            $table->timestamp('handover_at')->nullable();
            $table->timestamp('last_message_at')->nullable();
            $table->unsignedInteger('unread_count')->default(0);
            $table->timestamp('first_response_at')->nullable();
            $table->timestamp('resolved_at')->nullable();
            $table->timestamp('last_inbound_at')->nullable();
            $table->timestamps();

            $table->index('workspace_id');
            $table->index('channel_account_id');
            $table->index('contact_id');
        });

        Schema::create('messages', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('conversation_id');
            $table->string('direction', 16)->default('out');
            $table->string('channel', 32)->default('whatsapp');
            $table->string('type', 32)->default('text');
            $table->json('payload')->nullable();
            $table->text('body')->nullable();
            $table->unsignedBigInteger('media_id')->nullable();
            $table->string('status', 32)->default('sent');
            $table->string('provider_message_id', 255)->nullable();
            $table->json('error_json')->nullable();
            $table->string('sent_by', 64)->nullable();
            $table->unsignedBigInteger('user_id')->nullable();
            $table->timestamp('sent_at')->nullable();
            $table->timestamps();

            $table->foreign('conversation_id')->references('id')->on('conversations')->cascadeOnDelete();
            $table->index('provider_message_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('messages');
        Schema::dropIfExists('conversations');
    }
};
