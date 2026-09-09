import React, { useEffect, useRef } from "react";
import { Link } from "@inertiajs/react";
import ProductDashboard from "./ProductDashboard";

/* ─────────────────────────────────────────────
   Hub Notification - Interactive Hero Shell Styles
───────────────────────────────────────────── */
const STYLES = `
  @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700;900&display=swap');

  .qhero-shell,
  .qhero-shell *,
  .qhero-shell *::before,
  .qhero-shell *::after {
    box-sizing: border-box;
  }

  .qhero-shell {
    font-family: 'Space Grotesk', sans-serif;
    color-scheme: light;
    background: #F8FAFC;
    color: #0f172a;
  }

  /* ── Hero Headline & Glitch Effect ── */
  .qhero-glitch {
    position: relative;
    display: inline-block;
    font-weight: 900;
    color: #00A651;
    animation: qhero-color-toggle 7s infinite step-end;
  }
  .qhero-glitch::before,
  .qhero-glitch::after {
    content: attr(data-text);
    position: absolute;
    top: 0; left: 0;
    width: 100%; height: 100%;
    opacity: 0;
    background: #F8FAFC;
  }
  .qhero-glitch::before { animation: qhero-glitch-1 7s infinite linear; z-index: 2; }
  .qhero-glitch::after  { animation: qhero-glitch-2 7s infinite linear; z-index: 3; }

  @keyframes qhero-color-toggle {
    0%,  44%   { color: #0f172a; text-shadow: none; }
    42.1%, 44.9% { text-shadow: -2px 0 #10b981, 2px 0 #059669; }
    45%,  94%  { color: #00A651; text-shadow: none; }
    92.1%, 94.9% { text-shadow: -2px 0 #34d399, 2px 0 #047857; }
    95%, 100%  { color: #0f172a; text-shadow: none; }
  }

  @keyframes qhero-glitch-1 {
    0%,   42%  { opacity: 0; transform: translate(0); }
    42.1%      { opacity: 1; color: #10b981; clip-path: polygon(0 0,100% 0,100% 45%,0 45%); transform: translate(-10px,-5px) skew(20deg); }
    43%        { color: #059669; transform: translate(10px,5px) skew(-20deg); clip-path: polygon(0 10%,100% 0,100% 30%,0 35%); }
    44%        { color: #00A651; transform: translate(-10px,5px); clip-path: polygon(0 40%,100% 50%,100% 80%,0 90%); }
    44.9%      { opacity: 1; }
    45%        { opacity: 0; }
    45.1%, 92% { opacity: 0; transform: translate(0); }
    92.1%      { opacity: 1; color: #10b981; clip-path: polygon(0 60%,100% 55%,100% 100%,0 100%); transform: translate(10px,-5px) skew(10deg); }
    93%        { color: #059669; transform: translate(-5px,5px) skew(-10deg); clip-path: polygon(0 20%,100% 20%,100% 100%,0 80%); }
    94.9%      { opacity: 1; }
    95%        { opacity: 0; }
  }
  @keyframes qhero-glitch-2 {
    0%,   42%  { opacity: 0; transform: translate(0); }
    42.1%      { opacity: 1; color: #34d399; clip-path: polygon(0 55%,100% 55%,100% 100%,0 100%); transform: translate(10px,5px); }
    43%        { color: #047857; transform: translate(-10px,-5px) skew(10deg); clip-path: polygon(0 20%,100% 20%,100% 100%,0 80%); }
    45%        { opacity: 0; }
    45.1%, 92% { opacity: 0; transform: translate(0); }
    92.1%      { opacity: 1; color: #34d399; clip-path: polygon(0 0,100% 0,100% 45%,0 45%); transform: translate(-10px,-5px); }
    93%        { color: #047857; transform: translate(10px,5px) skew(-20deg); clip-path: polygon(0 10%,100% 0,100% 30%,0 35%); }
    95%        { opacity: 0; }
  }

  @keyframes qhero-pulse {
    0%, 100% { opacity: 1; }
    50%      { opacity: 0.65; }
  }
  .qhero-tagline-pulse { animation: qhero-pulse 2s ease-in-out infinite; }

  /* Hero CTA primary button */
  .qhero-btn-primary {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 14px 32px;
    border-radius: 9999px;
    background: #00A651;
    color: #fff;
    font-weight: 700;
    font-size: 1rem;
    border: none;
    cursor: pointer;
    text-decoration: none;
    font-family: 'Space Grotesk', sans-serif;
    transition: background 200ms, transform 200ms, box-shadow 200ms;
    box-shadow: 0 10px 30px rgba(0,166,81,0.28);
    position: relative;
    z-index: 30;
  }
  .qhero-btn-primary:hover { background: #059669; transform: scale(1.05); box-shadow: 0 14px 36px rgba(0,166,81,0.38); }
  .qhero-btn-primary:active { transform: scale(0.96); }
  .qhero-btn-primary svg { transition: transform 200ms; }
  .qhero-btn-primary:hover svg { transform: translateX(4px); }

  /* Hero CTA secondary button */
  .qhero-btn-secondary {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 14px 28px;
    border-radius: 9999px;
    background: #fff;
    color: #0f172a;
    font-weight: 700;
    font-size: 1rem;
    border: 1px solid #cbd5e1;
    cursor: pointer;
    text-decoration: none;
    font-family: 'Space Grotesk', sans-serif;
    transition: all 200ms;
    box-shadow: 0 4px 14px rgba(15,23,42,0.05);
    position: relative;
    z-index: 30;
  }
  .qhero-btn-secondary:hover { border-color: #00A651; color: #00A651; transform: scale(1.03); background: #f8fafc; }
`;

/* ─────────────────────────────────────────────
   Animated Canvas Dot Grid + Particle Simulation
───────────────────────────────────────────── */
function DotGridBackground() {
    const canvasRef = useRef(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        let raf;
        let mouseX = -1000;
        let mouseY = -1000;
        let isMobile = false;

        const SPACING = 32;
        const BASE_R = 1.5;
        const HOVER_R = 110;
        const SCAN_DUR = 2500;
        const SCAN_PAUSE = 4000;
        const DEFAULT_COLOR = "rgba(148,163,184,0.35)";
        const ACTIVE_COLOR = "#00A651";

        class Particle {
            constructor(w, h) {
                this.reset(w, h);
            }
            reset(w, h) {
                this.x = Math.random() * w;
                this.y = Math.random() * h;
                this.vx = (Math.random() - 0.5) * 0.4;
                this.vy = (Math.random() - 0.5) * 0.4;
                this.size = Math.random() * 2.2;
            }
            update(w, h) {
                this.x += this.vx;
                this.y += this.vy;
                if (this.x < 0 || this.x > w) this.vx *= -1;
                if (this.y < 0 || this.y > h) this.vy *= -1;
            }
            draw(c) {
                c.beginPath();
                c.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                c.fillStyle = "rgba(16,185,129,0.2)";
                c.fill();
            }
        }

        const particles = [];
        const resize = () => {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
            isMobile = window.innerWidth < 768;
            particles.length = 0;
            const n = isMobile ? 14 : 45;
            for (let i = 0; i < n; i++) particles.push(new Particle(canvas.width, canvas.height));
        };

        const onMouseMove = (e) => {
            if (isMobile) return;
            const r = canvas.getBoundingClientRect();
            mouseX = e.clientX - r.left;
            mouseY = e.clientY - r.top;
        };
        const onMouseLeave = () => {
            mouseX = -1000;
            mouseY = -1000;
        };

        const draw = () => {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            particles.forEach((p) => {
                p.update(canvas.width, canvas.height);
                p.draw(ctx);
            });

            const loopTime = performance.now() % (SCAN_DUR + SCAN_PAUSE);
            const scanY = isMobile
                ? (Math.min(loopTime / SCAN_DUR, 1)) * (canvas.height + HOVER_R * 2) - HOVER_R
                : 0;

            for (let x = 0; x < canvas.width; x += SPACING) {
                for (let y = 0; y < canvas.height; y += SPACING) {
                    const dx = x - mouseX, dy = y - mouseY;
                    const dist = isMobile ? Math.abs(y - scanY) : Math.sqrt(dx * dx + dy * dy);

                    if (dist < HOVER_R) {
                        const scale = 1 - dist / HOVER_R;
                        ctx.fillStyle = ACTIVE_COLOR;
                        ctx.shadowBlur = isMobile ? 0 : 12;
                        ctx.shadowColor = isMobile ? "transparent" : "rgba(16,185,129,0.45)";
                        ctx.beginPath();
                        ctx.arc(x, y, BASE_R + scale * (isMobile ? 2 : 3.2), 0, Math.PI * 2);
                        ctx.fill();
                    } else {
                        ctx.fillStyle = DEFAULT_COLOR;
                        ctx.shadowBlur = 0;
                        ctx.shadowColor = "transparent";
                        ctx.beginPath();
                        ctx.arc(x, y, BASE_R, 0, Math.PI * 2);
                        ctx.fill();
                    }
                }
            }
            raf = requestAnimationFrame(draw);
        };

        window.addEventListener("resize", resize);
        window.addEventListener("mousemove", onMouseMove);
        window.addEventListener("mouseleave", onMouseLeave);
        resize();
        draw();
        return () => {
            window.removeEventListener("resize", resize);
            window.removeEventListener("mousemove", onMouseMove);
            window.removeEventListener("mouseleave", onMouseLeave);
            cancelAnimationFrame(raf);
        };
    }, []);

    return (
        <div
            style={{
                position: "absolute",
                inset: 0,
                zIndex: 0,
                pointerEvents: "none",
                background: "#F8FAFC",
                colorScheme: "light",
            }}
        >
            <canvas ref={canvasRef} style={{ position: "absolute", inset: 0, display: "block" }} />

            {/* Soft Emerald Glow - Top Left */}
            <div
                style={{
                    position: "absolute",
                    top: "-15%",
                    left: "-5%",
                    width: "45%",
                    height: "45%",
                    background: "rgba(16,185,129,0.14)",
                    filter: "blur(130px)",
                    borderRadius: "9999px",
                }}
            />
            {/* Soft Emerald Glow - Bottom Right */}
            <div
                style={{
                    position: "absolute",
                    bottom: "-15%",
                    right: "-5%",
                    width: "40%",
                    height: "40%",
                    background: "rgba(52,211,153,0.12)",
                    filter: "blur(130px)",
                    borderRadius: "9999px",
                }}
            />

            {/* Giant Visual Decorative Brackets */}
            <div
                style={{
                    position: "absolute",
                    inset: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    pointerEvents: "none",
                    userSelect: "none",
                    opacity: 0.025,
                }}
            >
                <span style={{ fontSize: "38vw", fontWeight: 900, lineHeight: 1, color: "#000" }}>&lt;</span>
                <span style={{ width: "18vw" }} />
                <span style={{ fontSize: "38vw", fontWeight: 900, lineHeight: 1, color: "#000" }}>&gt;</span>
            </div>
        </div>
    );
}

/* ─────────────────────────────────────────────
   Main Hero Section Export
───────────────────────────────────────────── */
export default function HeroSection({ landing, canLogin, canRegister, auth }) {
    return (
        <section className="qhero-shell relative w-full overflow-hidden bg-[#F8FAFC]">
            <style>{STYLES}</style>

            {/* Canvas Dot Grid Background */}
            <DotGridBackground />

            {/* Main Hero Content & Layout */}
            <div className="relative z-20 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-12 pb-20 sm:pt-16 sm:pb-28">
                
                {/* Hero Header Typography */}
                <div className="text-center max-w-4xl mx-auto mb-10">
                    
                    {/* Animated Tagline Pulse */}
                    <span
                        className="qhero-tagline-pulse inline-block text-xs sm:text-sm font-black tracking-[0.26em] text-[#00A651] uppercase mb-4 px-4 py-1.5 rounded-full bg-emerald-50 border border-emerald-200/80 shadow-xs"
                    >
                        OMNICHANNEL NOTIFICATION GATEWAY & AI PLATFORM
                    </span>

                    {/* Headline with Glitch Effect */}
                    <h1 className="flex flex-col items-center font-black tracking-tight uppercase select-none my-0 leading-[0.88] text-slate-900 text-4xl sm:text-6xl md:text-7xl lg:text-8xl">
                        <span className="opacity-95 text-slate-900">SCALE YOUR</span>
                        <span className="qhero-glitch text-[#00A651]" data-text="NOTIFICATIONS">
                            NOTIFICATIONS
                        </span>
                    </h1>

                    {/* Subtitle */}
                    <p className="mt-6 text-base sm:text-lg md:text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed font-normal">
                        Deliver instant WhatsApp OTPs, transactional SMS, Email alerts, and AI-driven multi-channel automated workflows at scale. 99.99% uptime with high-throughput REST APIs.
                    </p>

                    {/* Hero Actions / Call to Actions */}
                    <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
                        {auth?.user ? (
                            <Link href={route('dashboard')} className="qhero-btn-primary">
                                <span>Open Dashboard</span>
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                    <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z" />
                                </svg>
                            </Link>
                        ) : (
                            <Link href={route('register')} className="qhero-btn-primary">
                                <span>Start Free Trial</span>
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                    <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z" />
                                </svg>
                            </Link>
                        )}

                        <a href="#pricing" className="qhero-btn-secondary">
                            <span>Explore API Docs</span>
                        </a>
                    </div>
                </div>

                {/* Dashboard Screenshot Graphic Container */}
                <div className="mt-10 sm:mt-14 max-w-5xl mx-auto">
                    <ProductDashboard />
                </div>

            </div>

            {/* Bottom Subtle Divider */}
            <div className="absolute bottom-0 inset-x-0 h-px bg-slate-200 pointer-events-none" />
        </section>
    );
}
