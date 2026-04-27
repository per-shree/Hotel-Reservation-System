// Configuration for the Hotel Reservation System
// Update the RAILWAY_URL when your backend is deployed!

const CONFIG = {
    // If you are running locally, it uses localhost. 
    // If you are on Vercel, it will use your Railway URL.
    API_BASE_URL: window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
        ? 'http://localhost:8080'
        : 'https://hotel-reservation-production-c9a7.up.railway.app' // REPLACE THIS with your actual Railway URL
};

window.API_CONFIG = CONFIG;
