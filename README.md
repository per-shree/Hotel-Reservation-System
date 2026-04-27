# Hotel Reservation System 🏨

A premium, real-time hotel management dashboard built with **Java** and a modern **Glassmorphism Web UI**. This system is now deployment-ready with MySQL integration and advanced management features.

## 🌟 Key Features
- **Real-time Executive Dashboard**: Monitor revenue, room availability, and recent bookings at a glance.
- **Advanced Room Management**: Bulk update pricing, add rooms dynamically, and toggle availability.
- **Future Date Bookings**: Integrated date-picking system for managing future guest arrivals.
- **Multi-Tenant Authentication**: Secure manager login/registration with data isolation by hotel.
- **Premium UX**: High-end dark mode interface with glassmorphism effects and dynamic animations.

## 🚀 Deployment Guide (Fully Working MySQL)

To get this project online with a functional MySQL database, I recommend using **Railway.app**.

### 1. Prepare your Project
- Push this code to a **GitHub Repository**.
- The project includes a `Dockerfile` for automatic cloud environment setup.

### 2. Setup on Railway
1. **Login** to [Railway.app](https://railway.app/).
2. Click **New Project** -> **Deploy from GitHub repo**.
3. Select this repository.
4. Add a **MySQL Database** service to your project.

### 3. Environment Variables
In your Railway dashboard, go to your **Java Service** settings -> **Variables** and add:
- `DB_URL`: The internal connection URL from your MySQL service.
- `DB_USER`: The MySQL username (usually `root`).
- `DB_PASSWORD`: The MySQL password.
- `PORT`: `8080`.

### 4. Database Sync
The server automatically verifies and creates necessary tables (`managers`, `reservations`, etc.) on startup.

---

## 🛠 Local Setup
1. **Database**: Ensure MySQL is running on `localhost:3306` (Database: `hotel_db`).
2. **Compile**:
   ```bash
   cd "Hotel Reservation System"
   javac -cp "mysql-connector-j-9.6.0.jar" HotelReservationApiServer.java
   ```
3. **Run**:
   ```bash
   java -cp ".;mysql-connector-j-9.6.0.jar" HotelReservationApiServer
   ```
4. **Access**: Open `http://localhost:8080` in your browser.

---

### Acknowledgments 🙏
- Developed as a modern upgrade to the classic Java Hotel System.
- Special thanks to the open-source community for the premium UI assets.
