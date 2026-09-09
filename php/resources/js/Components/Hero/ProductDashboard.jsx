import React from 'react';

export default function ProductDashboard() {
    return (
        <div className="relative w-full mx-auto max-w-[960px]">
            {/* Direct PNG Image Display without loading state or background card */}
            <img
                src="/assets/dashbord_with_socalmedia.png"
                alt="Hub Notification Omnichannel Admin Dashboard"
                className="w-full h-auto block object-contain mx-auto"
                loading="eager"
            />
        </div>
    );
}
