import React from 'react';
import ImagePlaceholder from './ImagePlaceholder';

export default function SocialChannels() {
    const channels = [
        {
            name: 'WhatsApp',
            src: '/assets/social/whatsapp.png',
            bg: 'bg-[#25D366]/10',
            border: 'border-[#25D366]/30',
            text: 'text-[#25D366]',
            badgeColor: '#25D366',
        },
        {
            name: 'Instagram',
            src: '/assets/social/instagram.png',
            bg: 'bg-[#E1306C]/10',
            border: 'border-[#E1306C]/30',
            text: 'text-[#E1306C]',
            badgeColor: '#E1306C',
        },
        {
            name: 'Messenger',
            src: '/assets/social/messenger.png',
            bg: 'bg-[#0084FF]/10',
            border: 'border-[#0084FF]/30',
            text: 'text-[#0084FF]',
            badgeColor: '#0084FF',
        },
        {
            name: 'Email',
            src: '/assets/social/email.png',
            bg: 'bg-[#F59E0B]/10',
            border: 'border-[#F59E0B]/30',
            text: 'text-[#F59E0B]',
            badgeColor: '#F59E0B',
        },
        {
            name: 'SMS',
            src: '/assets/social/sms.png',
            bg: 'bg-[#8B5CF6]/10',
            border: 'border-[#8B5CF6]/30',
            text: 'text-[#8B5CF6]',
            badgeColor: '#8B5CF6',
        },
    ];

    return (
        <div className="relative w-full mb-3 select-none">
            {/* 5 Top Floating Social Channel Cards */}
            <div className="flex items-center justify-between gap-2 sm:gap-4 px-2 sm:px-6">
                {channels.map((ch, idx) => (
                    <div
                        key={idx}
                        className="group flex flex-col items-center cursor-pointer transition-transform duration-300 hover:-translate-y-1"
                    >
                        {/* 2D Glass Card Container */}
                        <div className="flex items-center gap-2 rounded-2xl border border-slate-200/90 bg-white/95 backdrop-blur-md px-3 py-2 shadow-md shadow-emerald-900/5 transition-all group-hover:border-emerald-500 group-hover:shadow-lg">
                            <ImagePlaceholder
                                src={ch.src}
                                alt={ch.name}
                                fallback={ch.name}
                                className="h-7 w-7 rounded-xl object-contain"
                                iconColor={ch.text}
                                bgColor={ch.bg}
                                borderColor={ch.border}
                            />
                            <span className="hidden sm:inline text-xs font-bold text-slate-800 tracking-tight">
                                {ch.name}
                            </span>
                        </div>
                    </div>
                ))}
            </div>

            {/* Dotted Flowing Connector Lines SVG */}
            <div className="w-full h-8 relative pointer-events-none mt-1">
                <svg className="w-full h-full overflow-visible" viewBox="0 0 500 30" fill="none">
                    <path
                        d="M 50 2 C 50 20, 250 15, 250 28"
                        stroke="#00A651"
                        strokeWidth="1.5"
                        strokeDasharray="4 4"
                        opacity="0.5"
                    />
                    <path
                        d="M 150 2 C 150 18, 250 15, 250 28"
                        stroke="#00A651"
                        strokeWidth="1.5"
                        strokeDasharray="4 4"
                        opacity="0.6"
                    />
                    <path
                        d="M 250 2 L 250 28"
                        stroke="#00A651"
                        strokeWidth="2"
                        strokeDasharray="4 4"
                        opacity="0.8"
                    />
                    <path
                        d="M 350 2 C 350 18, 250 15, 250 28"
                        stroke="#00A651"
                        strokeWidth="1.5"
                        strokeDasharray="4 4"
                        opacity="0.6"
                    />
                    <path
                        d="M 450 2 C 450 20, 250 15, 250 28"
                        stroke="#00A651"
                        strokeWidth="1.5"
                        strokeDasharray="4 4"
                        opacity="0.5"
                    />
                    <circle cx="250" cy="28" r="3" fill="#00A651" />
                </svg>
            </div>
        </div>
    );
}
