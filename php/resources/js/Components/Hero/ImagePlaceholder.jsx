import React, { useState } from 'react';

export default function ImagePlaceholder({
    src,
    alt = '',
    fallback = '',
    className = '',
    width,
    height,
    iconColor = 'text-emerald-600',
    bgColor = 'bg-emerald-50',
    borderColor = 'border-emerald-200',
}) {
    const [hasError, setHasError] = useState(false);

    if (hasError || !src) {
        return (
            <div
                style={{ width: width ? `${width}px` : undefined, height: height ? `${height}px` : undefined }}
                className={`inline-flex items-center justify-center rounded-xl border ${borderColor} ${bgColor} ${className} font-bold text-xs shadow-xs transition-all flex-shrink-0 select-none p-1.5`}
                title={alt || fallback}
            >
                <div className="flex items-center gap-1">
                    <span className={`font-semibold ${iconColor} text-[11px] truncate max-w-[90px]`}>
                        {fallback || alt || 'Icon'}
                    </span>
                </div>
            </div>
        );
    }

    return (
        <img
            src={src}
            alt={alt}
            width={width}
            height={height}
            onError={() => setHasError(true)}
            className={`object-contain flex-shrink-0 ${className}`}
        />
    );
}
