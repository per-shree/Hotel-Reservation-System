let reservations = [
    { id: 1, guest: "John Doe", room: 101, contact: "9876543210", date: "2024-04-24", status: "Confirmed" },
    { id: 2, guest: "Jane Smith", room: 205, contact: "8765432109", date: "2024-04-23", status: "Pending" },
    { id: 3, guest: "Robert Wilson", room: 302, contact: "7654321098", date: "2024-04-22", status: "Confirmed" }
];

function renderTable() {
    const tbody = document.getElementById('reservation-table');
    tbody.innerHTML = reservations.map(res => `
        <tr>
            <td>
                <div style="display: flex; align-items: center; gap: 0.75rem">
                    <div style="width: 35px; height: 35px; border-radius: 50%; background: var(--accent-color); display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 0.8rem">
                        ${res.guest.split(' ').map(n => n[0]).join('')}
                    </div>
                    ${res.guest}
                </div>
            </td>
            <td>Room ${res.room}</td>
            <td>${res.contact}</td>
            <td>${res.date}</td>
            <td><span class="status status-${res.status.toLowerCase()}">${res.status}</span></td>
            <td>
                <button style="background: none; border: none; color: var(--text-secondary); cursor: pointer"><i class="fas fa-ellipsis-v"></i></button>
            </td>
        </tr>
    `).join('');
}

function openModal() {
    document.getElementById('reservationModal').style.display = 'flex';
}

function closeModal() {
    document.getElementById('reservationModal').style.display = 'none';
}

document.getElementById('resForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const newRes = {
        id: reservations.length + 1,
        guest: document.getElementById('guestName').value,
        room: document.getElementById('roomNumber').value,
        contact: document.getElementById('contact').value,
        date: new Date().toISOString().split('T')[0],
        status: "Confirmed"
    };
    reservations.unshift(newRes);
    renderTable();
    closeModal();
    e.target.reset();
});

// Initialize
renderTable();
