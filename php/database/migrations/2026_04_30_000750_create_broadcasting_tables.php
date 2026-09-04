<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('sms_provider_configs', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('workspace_id');
            $table->string('provider', 32);
            $table->text('credentials')->nullable();
            $table->string('sender_id', 64)->nullable();
            $table->boolean('default')->default(false);
            $table->timestamps();

            $table->index(['workspace_id', 'provider']);
        });

        Schema::create('campaigns', function (Blueprint $table) {
            $table->id();
            $table->uuid('uuid')->unique();
            $table->unsignedBigInteger('workspace_id');
            $table->string('name', 255);
            $table->string('channel', 32);
            $table->string('whatsapp_phone_number_id', 64)->nullable();
            $table->string('audience_type', 64)->default('all');
            $table->string('audience_ref', 128)->nullable();
            $table->json('template_ref')->nullable();
            $table->json('payload_json')->nullable();
            $table->timestamp('schedule_at')->nullable();
            $table->string('timezone', 64)->nullable();
            $table->string('status', 32)->default('draft');
            $table->json('totals_json')->nullable();
            $table->unsignedBigInteger('created_by')->nullable();
            $table->timestamps();

            $table->index('workspace_id');
        });

        Schema::create('campaign_recipients', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('campaign_id');
            $table->unsignedBigInteger('contact_id');
            $table->string('status', 32)->default('queued');
            $table->string('provider_message_id', 255)->nullable();
            $table->string('tracking_token', 128)->nullable();
            $table->string('unsubscribe_token', 128)->nullable();
            $table->timestamp('sent_at')->nullable();
            $table->timestamp('delivered_at')->nullable();
            $table->timestamp('read_at')->nullable();
            $table->timestamp('clicked_at')->nullable();
            $table->timestamp('opted_out_at')->nullable();
            $table->text('failed_reason')->nullable();
            $table->timestamps();

            $table->foreign('campaign_id')->references('id')->on('campaigns')->cascadeOnDelete();
            $table->foreign('contact_id')->references('id')->on('contacts')->cascadeOnDelete();
        });

        Schema::create('usage_meters', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('workspace_id');
            $table->string('metric', 64);
            $table->integer('period');
            $table->integer('value')->default(0);
            $table->timestamps();

            $table->unique(['workspace_id', 'metric', 'period']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('usage_meters');
        Schema::dropIfExists('campaign_recipients');
        Schema::dropIfExists('campaigns');
        Schema::dropIfExists('sms_provider_configs');
    }
};
