import React, { useEffect, useRef, useState } from 'react';

export default function Hero3DScene() {
    const containerRef = useRef(null);
    const [simStep, setSimStep] = useState(0);
    const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
    const [isReducedMotion, setIsReducedMotion] = useState(false);

    // Live Automated Conversation Sequence Loop
    useEffect(() => {
        const interval = setInterval(() => {
            setSimStep((prev) => (prev + 1) % 5);
        }, 3400);
        return () => clearInterval(interval);
    }, []);

    // Check prefers-reduced-motion
    useEffect(() => {
        const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
        setIsReducedMotion(mediaQuery.matches);

        const handleChange = (e) => setIsReducedMotion(e.matches);
        mediaQuery.addEventListener('change', handleChange);
        return () => mediaQuery.removeEventListener('change', handleChange);
    }, []);

    // Parallax Mouse Handler
    useEffect(() => {
        if (isReducedMotion) return;
        const handleMouseMove = (e) => {
            const { innerWidth, innerHeight } = window;
            const x = (e.clientX / innerWidth - 0.5) * 12; // degrees / offset
            const y = (e.clientY / innerHeight - 0.5) * 10;
            setMousePos({ x, y });
        };
        window.addEventListener('mousemove', handleMouseMove);
        return () => window.removeEventListener('mousemove', handleMouseMove);
    }, [isReducedMotion]);

    return (
        <div
            ref={containerRef}
            className="relative w-full h-[580px] sm:h-[650px] lg:h-[720px] select-none flex items-center justify-center overflow-visible"
        >
            {/* Background Wall Decor & Handwritten Style Annotations */}

            {/* Wall Picture Frame (Top Right) */}
            <div className="absolute top-2 right-4 sm:right-8 z-0 hidden sm:block transform rotate-2 rounded-xl border-4 border-[#D4A373]/30 bg-[#FFFDF9] p-3 shadow-md w-36">
                <div className="text-[10px] font-bold text-slate-700 font-serif leading-tight">
                    Ideas today,
                </div>
                <div className="text-[12px] font-extrabold text-blue-600 font-serif leading-tight mt-1">
                    A bigger tomorrow
                </div>
                <div className="text-right text-xs text-amber-500 font-bold mt-1">🙂</div>
            </div>

            {/* Sticky Note (Bottom Right) */}
            <div className="absolute bottom-16 right-2 sm:right-6 z-0 hidden sm:block transform -rotate-3 rounded-lg bg-amber-200/90 p-3 shadow-md w-32 border-b-2 border-amber-300">
                <div className="text-[11px] font-bold text-slate-800 leading-snug">
                    Small Business
                </div>
                <div className="text-[13px] font-extrabold text-slate-900 leading-snug">
                    Big Opportunities
                </div>
                <div className="text-center text-xs text-rose-500 font-bold mt-0.5">♡</div>
            </div>

            {/* Handwritten Style Annotation (Top Center) */}
            <div className="absolute top-12 left-1/3 z-10 hidden lg:flex flex-col items-center pointer-events-none transform -rotate-6">
                <span className="font-handwriting text-blue-600 font-bold text-base tracking-wide drop-shadow-sm">
                    All your messages in one place!
                </span>
                <svg className="h-6 w-8 text-blue-500 transform rotate-45 mt-0.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 13.5L12 21m0 0l-7.5-7.5M12 21V3" />
                </svg>
            </div>

            {/* 3D Scene Layer Group with Parallax Shift */}
            <div
                className="relative w-full h-full flex items-center justify-center transition-transform duration-300 ease-out"
                style={{
                    transform: `perspective(1200px) rotateY(${mousePos.x * 0.4}deg) rotateX(${-mousePos.y * 0.3}deg)`,
                }}
            >
                {/* 1. TOP FLOATING 3D CHANNEL BADGES & CONNECTOR WIRES */}
                <div className="absolute top-4 sm:top-8 left-1/2 transform -translate-x-1/2 z-30 flex items-center gap-3 sm:gap-5">
                    
                    {/* WhatsApp 3D Badge */}
                    <div className="flex flex-col items-center group animate-float-slow">
                        <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-[#25D366] text-white shadow-lg shadow-emerald-500/30 border-2 border-white/60 transform hover:scale-110 transition-transform">
                            <svg className="h-7 w-7 sm:h-8 sm:w-8 fill-current" viewBox="0 0 24 24">
                                <path d="M.057 24l1.687-6.163a11.867 11.867 0 01-1.587-5.945C.16 5.335 5.495 0 12.05 0a11.817 11.817 0 018.413 3.488 11.824 11.824 0 013.48 8.414c-.003 6.557-5.338 11.892-11.893 11.892a11.9 11.9 0 01-5.688-1.448L.057 24z" />
                            </svg>
                        </div>
                        {/* Curved Wire Connector */}
                        <div className="h-10 w-0.5 bg-gradient-to-b from-[#25D366] to-blue-400 opacity-80" />
                    </div>

                    {/* Messenger 3D Badge */}
                    <div className="flex flex-col items-center group animate-float-medium">
                        <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-[#0084FF] text-white shadow-lg shadow-blue-500/30 border-2 border-white/60 transform hover:scale-110 transition-transform">
                            <svg className="h-7 w-7 sm:h-8 sm:w-8 fill-current" viewBox="0 0 24 24">
                                <path d="M12 0C5.373 0 0 4.974 0 11.111c0 3.498 1.744 6.614 4.469 8.654V24l4.088-2.242c1.092.301 2.246.464 3.443.464 6.627 0 12-4.975 12-11.111C24 4.974 18.627 0 12 0zm1.191 14.963l-3.055-3.26-5.963 3.26L10.732 8.1l3.131 3.259L19.752 8.1l-6.561 6.863z" />
                            </svg>
                        </div>
                        <div className="h-10 w-0.5 bg-gradient-to-b from-[#0084FF] to-blue-400 opacity-80" />
                    </div>

                    {/* Instagram 3D Badge */}
                    <div className="flex flex-col items-center group animate-float-fast">
                        <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-gradient-to-tr from-amber-500 via-rose-500 to-purple-600 text-white shadow-lg shadow-pink-500/30 border-2 border-white/60 transform hover:scale-110 transition-transform">
                            <svg className="h-7 w-7 sm:h-8 sm:w-8" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                                <rect x="2" y="2" width="20" height="20" rx="5" ry="5" />
                                <path d="M16 11.37A4 4 0 1112.63 8 4 4 0 0116 11.37z" />
                                <line x1="17.5" y1="6.5" x2="17.51" y2="6.5" strokeLinecap="round" />
                            </svg>
                        </div>
                        <div className="h-10 w-0.5 bg-gradient-to-b from-rose-500 to-blue-400 opacity-80" />
                    </div>

                    {/* Email 3D Badge */}
                    <div className="flex flex-col items-center group animate-float-slow">
                        <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-amber-500 text-white shadow-lg shadow-amber-500/30 border-2 border-white/60 transform hover:scale-110 transition-transform">
                            <svg className="h-7 w-7 sm:h-8 sm:w-8" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
                            </svg>
                        </div>
                        <div className="h-10 w-0.5 bg-gradient-to-b from-amber-500 to-blue-400 opacity-80" />
                    </div>

                    {/* SMS 3D Badge */}
                    <div className="flex flex-col items-center group animate-float-medium">
                        <div className="flex h-12 w-12 sm:h-14 sm:w-14 items-center justify-center rounded-2xl bg-purple-600 text-white shadow-lg shadow-purple-500/30 border-2 border-white/60 transform hover:scale-110 transition-transform">
                            <span className="text-xs sm:text-sm font-extrabold tracking-wider">SMS</span>
                        </div>
                        <div className="h-10 w-0.5 bg-gradient-to-b from-purple-600 to-blue-400 opacity-80" />
                    </div>
                </div>

                {/* 2. CENTRAL 3D GLASS PRODUCT DASHBOARD (RIGHT-CENTER ALIGNED) */}
                <div
                    className="relative z-20 w-[92%] sm:w-[82%] lg:w-[78%] max-w-[560px] rounded-3xl border-2 border-white bg-white/90 backdrop-blur-2xl shadow-2xl shadow-blue-900/15 p-4 sm:p-6 transition-transform duration-500 transform rotate-1"
                    style={{
                        boxShadow: '0 25px 50px -12px rgba(14, 165, 233, 0.25), 0 0 0 1px rgba(255, 255, 255, 0.8)',
                    }}
                >
                    {/* Dashboard Header */}
                    <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-4">
                        <div className="flex items-center gap-2">
                            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-blue-600 text-white font-bold shadow-sm">
                                <svg className="h-4 w-4 fill-current" viewBox="0 0 24 24">
                                    <path d="M12 2C6.477 2 2 6.477 2 12c0 1.821.487 3.53 1.338 5L2.5 21.5l4.632-.816A9.957 9.957 0 0012 22c5.523 0 10-4.477 10-10S17.523 2 12 2z" />
                                </svg>
                            </div>
                            <span className="text-sm font-extrabold text-slate-900 tracking-tight">
                                Hub Notification
                            </span>
                        </div>

                        <div className="flex items-center gap-2">
                            <span className="flex h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                            <span className="text-xs font-semibold text-emerald-600 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
                                Meta Verified • Online
                            </span>
                        </div>
                    </div>

                    {/* Sidebar Tabs & Conversation Body */}
                    <div className="grid grid-cols-12 gap-3 sm:gap-4">
                        {/* Sidebar Navigation */}
                        <div className="col-span-4 sm:col-span-3 space-y-1 text-xs font-semibold text-slate-500 pr-2 border-r border-slate-100">
                            <div className="flex items-center gap-2 rounded-xl bg-blue-600 text-white px-3 py-2 font-bold shadow-md shadow-blue-500/20">
                                <span>Inbox</span>
                                <span className="ml-auto rounded-full bg-white/20 text-[10px] px-1.5 py-0.2">12</span>
                            </div>
                            <div className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 rounded-xl transition-colors">
                                👤 <span>Contacts</span>
                            </div>
                            <div className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 rounded-xl transition-colors">
                                📢 <span>Broadcasts</span>
                            </div>
                            <div className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 rounded-xl transition-colors">
                                ⚡ <span>Automation</span>
                            </div>
                            <div className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 rounded-xl transition-colors">
                                📊 <span>Analytics</span>
                            </div>
                            <div className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 rounded-xl transition-colors">
                                ⚙️ <span>Settings</span>
                            </div>
                        </div>

                        {/* Live Conversation Stream */}
                        <div className="col-span-8 sm:col-span-9 space-y-2.5">
                            
                            {/* Message 1: WhatsApp Customer */}
                            <div className="rounded-2xl border border-slate-100 bg-slate-50/80 p-3 transition-all hover:bg-white hover:shadow-md">
                                <div className="flex items-start justify-between">
                                    <div className="flex items-center gap-2">
                                        <div className="h-8 w-8 rounded-full bg-gradient-to-tr from-purple-500 to-indigo-500 text-white font-bold text-xs flex items-center justify-center shadow-sm">
                                            AR
                                        </div>
                                        <div>
                                            <div className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                                                Alex Rivers
                                                <span className="h-2 w-2 rounded-full bg-emerald-500" />
                                            </div>
                                            <div className="text-[11px] text-slate-500">via WhatsApp Business</div>
                                        </div>
                                    </div>
                                    <span className="text-[10px] font-semibold text-slate-400">Just now</span>
                                </div>
                                <p className="mt-1.5 text-xs text-slate-700 font-medium">
                                    "Hey! Can your AI handle bulk WhatsApp broadcasts and instant automated replies?"
                                </p>
                            </div>

                            {/* Message 2: AI Reply Generated */}
                            {simStep >= 1 && (
                                <div className="rounded-2xl border border-blue-200 bg-blue-50/90 p-3 animate-fade-in shadow-sm">
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-1.5 text-xs font-extrabold text-blue-700">
                                            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-blue-600 text-white text-[10px]">AI</span>
                                            Instant AI Chatbot Reply
                                        </div>
                                        <span className="text-[10px] font-bold text-emerald-600 bg-emerald-100/80 px-2 py-0.5 rounded-full">
                                            0.4s response
                                        </span>
                                    </div>
                                    <p className="mt-1 text-xs text-blue-900 font-medium leading-relaxed">
                                        "Yes Alex! Hub Notification handles zero-code AI automated replies, bulk broadcasts & Meta verified CRM workflows."
                                    </p>
                                </div>
                            )}

                            {/* Tag & Conversion Status */}
                            <div className="flex items-center justify-between pt-1">
                                <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold transition-all ${simStep >= 3 ? 'bg-emerald-100 text-emerald-700 border border-emerald-300' : 'bg-slate-100 text-slate-500'}`}>
                                    ✓ {simStep >= 3 ? 'Lead Qualified & Tagged' : 'Processing Lead...'}
                                </span>
                                <span className="text-xs font-extrabold text-slate-900 bg-emerald-50 text-emerald-700 border border-emerald-200 px-2.5 py-0.5 rounded-lg">
                                    +₹42,500 Revenue
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* 3. 3D CHARACTER & DESK SCENE (LEFT ALIGNED IN 3D COMPOSITION) */}
                <div className="absolute bottom-0 left-0 sm:left-4 z-30 pointer-events-none transform -translate-x-4 sm:translate-x-0">
                    
                    {/* 3D Stylized Young Professional Character & Desk Container */}
                    <div className="relative flex flex-col items-center">
                        
                        {/* 3D Character Illustration Avatar */}
                        <div className="relative z-10 w-44 sm:w-56 h-48 sm:h-60 flex items-center justify-center">
                            {/* Stylized 3D Avatar Rendering */}
                            <svg className="w-full h-full drop-shadow-xl" viewBox="0 0 200 220" fill="none">
                                {/* Character Hair */}
                                <path d="M60 70C60 35 140 35 140 70C145 60 135 40 120 35C105 30 85 30 75 40C65 50 55 60 60 70Z" fill="#5A3825" />
                                
                                {/* Head & Face */}
                                <circle cx="100" cy="85" r="38" fill="#FCE0D1" />
                                <path d="M72 70C72 50 128 50 128 70C128 75 72 75 72 70Z" fill="#4A2E1F" />
                                
                                {/* Friendly Smiling Face Features */}
                                <circle cx="88" cy="82" r="5" fill="#2D3748" />
                                <circle cx="112" cy="82" r="5" fill="#2D3748" />
                                <path d="M90 98Q100 108 110 98" stroke="#E53E3E" strokeWidth="3" strokeLinecap="round" fill="none" />
                                
                                {/* Blue Hoodie Body */}
                                <path d="M50 150C50 125 70 120 100 120C130 120 150 125 150 150L155 210H45L50 150Z" fill="#1B64F2" />
                                
                                {/* Hub Notification Badge on Hoodie */}
                                <circle cx="100" cy="148" r="10" fill="#FFFFFF" />
                                <circle cx="97" cy="147" r="2" fill="#1B64F2" />
                                <circle cx="103" cy="147" r="2" fill="#1B64F2" />
                                
                                {/* Pointing Right Arm towards Dashboard */}
                                <path d="M140 145Q170 130 185 115" stroke="#FCE0D1" strokeWidth="14" strokeLinecap="round" fill="none" />
                                <path d="M140 145Q170 130 185 115" stroke="#1B64F2" strokeWidth="18" strokeLinecap="round" fill="none" mask="url(#armMask)" />
                            </svg>

                            {/* Character Chair Backrest */}
                            <div className="absolute top-16 left-2 z-0 h-36 w-10 rounded-2xl bg-slate-800 border-2 border-slate-700 shadow-lg" />
                        </div>

                        {/* Desk Surface & Office Desk Accessories */}
                        <div className="relative z-20 -mt-10 w-64 sm:w-80 rounded-2xl border-2 border-[#D4A373] bg-gradient-to-r from-[#E6B88A] via-[#F3D2B3] to-[#E6B88A] p-2.5 shadow-xl">
                            <div className="flex items-center justify-between px-2">
                                
                                {/* Open Laptop */}
                                <div className="flex flex-col items-center">
                                    <div className="h-14 w-20 rounded-t-lg bg-slate-800 border-2 border-slate-700 flex items-center justify-center text-white text-[10px] font-bold shadow-inner">
                                        <div className="h-3 w-3 rounded-full bg-blue-500 animate-ping" />
                                    </div>
                                    <div className="h-2 w-24 rounded-b-md bg-slate-300 border-t border-slate-400" />
                                </div>

                                {/* Mug ("Big Ideas 🙂") */}
                                <div className="flex flex-col items-center">
                                    <div className="h-8 w-7 rounded-lg bg-white border border-slate-200 shadow-sm flex flex-col items-center justify-center p-0.5">
                                        <span className="text-[7px] font-extrabold text-slate-800 leading-tight">Big</span>
                                        <span className="text-[6px] font-bold text-blue-600 leading-tight">Ideas</span>
                                        <span className="text-[7px]">🙂</span>
                                    </div>
                                </div>

                                {/* Stacked Books ("Automate", "Engage", "Grow") */}
                                <div className="flex flex-col space-y-0.5">
                                    <div className="h-3.5 w-16 rounded bg-blue-600 text-white text-[8px] font-bold flex items-center justify-center shadow-xs">
                                        Automate
                                    </div>
                                    <div className="h-3.5 w-16 rounded bg-emerald-500 text-white text-[8px] font-bold flex items-center justify-center shadow-xs">
                                        Engage
                                    </div>
                                    <div className="h-3.5 w-16 rounded bg-amber-500 text-white text-[8px] font-bold flex items-center justify-center shadow-xs">
                                        Grow
                                    </div>
                                </div>

                                {/* Small Plant */}
                                <div className="flex flex-col items-center">
                                    <div className="text-sm">🌱</div>
                                    <div className="h-4 w-5 rounded-b-md bg-slate-100 border border-slate-300" />
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* 4. AI ASSISTANT ROBOT (RIGHT ALIGNED NEAR DASHBOARD) */}
                <div className="absolute top-1/4 right-2 sm:right-6 z-30 flex flex-col items-center animate-float-medium">
                    
                    {/* Speech Bubble: "AI Replies 24/7 ✨" */}
                    <div className="mb-2 rounded-2xl border border-blue-200 bg-white/95 backdrop-blur-md px-3 py-1.5 shadow-lg shadow-blue-500/10 text-center">
                        <span className="text-xs font-extrabold text-slate-900 flex items-center gap-1">
                            AI Replies 24/7 <span className="text-amber-500">✨</span>
                        </span>
                    </div>

                    {/* 3D Cute AI Robot Avatar */}
                    <div className="relative flex h-20 w-20 sm:h-24 sm:w-24 items-center justify-center rounded-full bg-gradient-to-b from-white to-blue-50 border-4 border-white shadow-xl shadow-blue-500/20">
                        {/* Antenna */}
                        <div className="absolute -top-3 h-4 w-1 bg-blue-500 rounded-full flex items-center justify-center">
                            <div className="h-2.5 w-2.5 rounded-full bg-sky-400 animate-ping" />
                        </div>

                        {/* Visor Screen & Smiling Eyes */}
                        <div className="h-10 w-14 rounded-2xl bg-slate-900 border-2 border-blue-400 flex items-center justify-around px-2">
                            <div className="h-2.5 w-2.5 rounded-full bg-sky-400 animate-pulse" />
                            <div className="h-2.5 w-2.5 rounded-full bg-sky-400 animate-pulse" />
                        </div>

                        {/* Chest "AI" Badge */}
                        <div className="absolute bottom-2 flex h-5 w-8 items-center justify-center rounded-md bg-blue-600 text-white font-extrabold text-[10px] shadow-sm">
                            AI
                        </div>
                    </div>
                </div>

                {/* 5. FLOOR ACCENTS (GOLDEN RETRIEVER PUPPY & BRAND SPHERE) */}
                
                {/* Sleeping Golden Retriever Pup (Bottom Left Floor) */}
                <div className="absolute bottom-2 left-8 sm:left-16 z-20 hidden sm:flex items-center gap-2">
                    <div className="relative h-14 w-20 rounded-full bg-[#E5A93C] border-2 border-[#C88E28] shadow-md flex items-center justify-center p-2">
                        <span className="text-xl">🐶💤</span>
                    </div>
                </div>

                {/* Blue Brand Sphere ("Good Conversations Happier Business") */}
                <div className="absolute bottom-4 left-32 sm:left-48 z-20 hidden sm:flex items-center justify-center h-16 w-16 rounded-full bg-gradient-to-tr from-blue-700 to-sky-400 text-white font-extrabold text-[8px] text-center p-2 shadow-lg shadow-blue-500/30 border-2 border-white transform rotate-6">
                    Good Conversations Happier Business 🙂
                </div>

                {/* Annotation (Bottom Center) */}
                <div className="absolute bottom-2 right-1/3 z-10 hidden lg:flex items-center gap-1.5 pointer-events-none transform rotate-3">
                    <svg className="h-5 w-6 text-blue-500" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 10.5L12 3m0 0l7.5 7.5M12 3v18" />
                    </svg>
                    <span className="font-handwriting text-blue-600 font-bold text-xs">
                        Automate Today Grow Tomorrow 🙂
                    </span>
                </div>

            </div>
        </div>
    );
}
