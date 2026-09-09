import { Link } from '@inertiajs/react';
import { useTranslation } from 'react-i18next';

export default function HeroContent({ landing, auth, canRegister }) {
    const { t } = useTranslation();
    const s = (key, def = '') => landing?.[`landing.${key}`] ?? def;

    const eyebrow = s('hero_badge') || 'AI-powered omnichannel communication';
    const subtitle = s('hero_subtitle') || 'Unify WhatsApp, Messenger and Instagram, automate replies with AI chatbots, run bulk broadcasts, and turn conversations into revenue — all from one platform.';
    const primaryCta = s('hero_cta_primary') || 'Get Started Free';
    const secondaryCta = s('hero_cta_secondary') || 'Watch Demo';

    const metrics = [
        {
            value: '50M+',
            label: 'Messages delivered',
            icon: (
                <svg className="h-5 w-5 text-[#00A651]" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3 21l18-9L3 3l3 9zm0 0h75" />
                </svg>
            )
        },
        {
            value: '12,000+',
            label: 'Businesses',
            icon: (
                <svg className="h-5 w-5 text-[#00A651]" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
                </svg>
            )
        },
        {
            value: '99.9%',
            label: 'Uptime SLA',
            icon: (
                <svg className="h-5 w-5 text-[#00A651]" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
                </svg>
            )
        },
        {
            value: '5x',
            label: 'Higher open rates',
            icon: (
                <svg className="h-5 w-5 text-[#00A651]" fill="none" stroke="currentColor" strokeWidth={2.2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941" />
                </svg>
            )
        },
    ];

    return (
        <div className="flex flex-col justify-center py-4 lg:py-6 select-none relative z-20">
            
            {/* Top Eyebrow Badge */}
            <div className="inline-flex items-center gap-2 self-start rounded-full border border-emerald-300/80 bg-emerald-50 px-4 py-1.5 shadow-xs transition-all hover:bg-emerald-100/60 mb-6">
                <span className="text-emerald-600 font-extrabold text-xs">✨</span>
                <span className="text-xs sm:text-sm font-bold tracking-wide text-emerald-800">
                    {eyebrow}
                </span>
            </div>

            {/* Main Marketing Headline - Accent Green on "Conversation," */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.12]">
                Every Customer{' '}
                <span className="block text-[#00A651] drop-shadow-xs">
                    Conversation,
                </span>
                <span className="block text-slate-900">
                    One Smart Inbox
                </span>
            </h1>

            {/* Supporting Description */}
            <p className="mt-5 text-base sm:text-lg text-slate-600 max-w-lg leading-relaxed font-normal">
                {subtitle}
            </p>

            {/* CTA Buttons - Matching Reference Pill Styling */}
            <div className="mt-8 flex flex-wrap items-center gap-3 sm:gap-4">
                {auth?.user ? (
                    <Link
                        href={route('client.dashboard')}
                        className="group relative inline-flex items-center gap-2.5 rounded-full bg-[#00A651] px-8 py-4 text-base font-bold text-white shadow-xl shadow-emerald-600/25 transition-all duration-300 hover:bg-emerald-700 hover:scale-[1.02] active:scale-[0.98]"
                    >
                        <span>{t('welcome.goToDashboard', 'Go to Dashboard')}</span>
                        <svg className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                        </svg>
                    </Link>
                ) : (
                    <>
                        <Link
                            href={route('register')}
                            className="group relative inline-flex items-center gap-2.5 rounded-full bg-[#00A651] px-8 py-4 text-base font-bold text-white shadow-xl shadow-emerald-600/25 transition-all duration-300 hover:bg-emerald-700 hover:scale-[1.02] active:scale-[0.98]"
                        >
                            <span>{primaryCta} →</span>
                        </Link>

                        <a
                            href="#demo"
                            onClick={(e) => {
                                e.preventDefault();
                                const el = document.getElementById('demo') || document.getElementById('features') || document.getElementById('pricing');
                                el?.scrollIntoView({ behavior: 'smooth' });
                            }}
                            className="inline-flex items-center gap-2.5 rounded-full border border-slate-200 bg-white/95 backdrop-blur-md px-7 py-4 text-base font-semibold text-slate-800 shadow-sm transition-all duration-300 hover:bg-white hover:border-slate-300 hover:shadow active:scale-[0.98]"
                        >
                            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-100 text-[#00A651]">
                                <svg className="h-3.5 w-3.5 fill-current ml-0.5" viewBox="0 0 24 24">
                                    <path d="M8 5v14l11-7z" />
                                </svg>
                            </span>
                            {secondaryCta}
                        </a>
                    </>
                )}
            </div>

            {/* Compact Trust Badges matching Reference Icons */}
            <div className="mt-7 flex flex-wrap items-center gap-5 text-xs font-semibold text-slate-700">
                <div className="flex items-center gap-1.5">
                    <span className="flex h-4 w-4 items-center justify-center rounded-full bg-[#00A651] text-white font-bold text-[10px]">
                        ✓
                    </span>
                    <span>No credit card required</span>
                </div>

                <div className="flex items-center gap-1.5">
                    <span className="text-purple-600">📅</span>
                    <span>14-day free trial</span>
                </div>

                <div className="flex items-center gap-1.5">
                    <svg className="h-4 w-4 text-[#00A651] fill-current" viewBox="0 0 24 24">
                        <path d="M12 2.04c-5.5 0-10 4.49-10 10.02 0 5 3.66 9.15 8.44 9.9v-7h-2.54v-2.9h2.54V9.85c0-2.51 1.49-3.89 3.78-3.89 1.09 0 2.23.19 2.23.19v2.47h-1.26c-1.24 0-1.63.77-1.63 1.56v1.88h2.78l-.45 2.9h-2.33v7a10 10 0 008.44-9.9c0-5.53-4.5-10.02-10-10.02z" />
                    </svg>
                    <span>Official Meta APIs</span>
                </div>
            </div>

            {/* Handwritten Accent Text (`Connect Automate Grow ↗`) */}
            <div className="mt-5 hidden sm:flex items-center gap-2 text-emerald-700 font-bold text-xs transform -rotate-2">
                <span>Connect · Automate · Grow</span>
                <svg className="h-4 w-5 text-[#00A651]" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 19.5l15-15m0 0H8.25m11.25 0v11.25" />
                </svg>
            </div>

            {/* Bottom Metrics Bar */}
            <div className="mt-10 grid grid-cols-2 sm:grid-cols-4 gap-4 border-t border-slate-200/80 pt-6">
                {metrics.map((m, idx) => (
                    <div key={idx} className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-emerald-50 border border-emerald-100 flex-shrink-0 shadow-xs">
                            {m.icon}
                        </div>
                        <div className="flex flex-col">
                            <span className="text-lg sm:text-xl font-extrabold text-slate-900 tracking-tight leading-none">
                                {m.value}
                            </span>
                            <span className="text-[11px] text-slate-500 font-medium mt-1 leading-tight">
                                {m.label}
                            </span>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
