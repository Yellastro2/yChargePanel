document.addEventListener('DOMContentLoaded', function() {
    const currentHost = window.location.origin; // Определяет текущий адрес
    const apiUrl = `${currentHost}/api/stationList`;
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            const table = document.getElementById('station_table');
            data.forEach((station, index) => {
                const row = document.createElement('tr');
                row.setAttribute('data-index', index);

                const stIdCell = document.createElement('td');
                stIdCell.textContent = station.stId;
                row.appendChild(stIdCell);

                const sizeCell = document.createElement('td');
                sizeCell.textContent = station.size;
                row.appendChild(sizeCell);

                const availableCell = document.createElement('td');
                availableCell.textContent = station.available;
                row.appendChild(availableCell);

                const timestampCell = document.createElement('td');
                const date = new Date(station.timestamp * 1000);
                const formattedDate = date.toLocaleString('ru-RU', {
                    day: '2-digit',
                    month: '2-digit',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });
                timestampCell.textContent = formattedDate;
                row.appendChild(timestampCell);

                if (station.trafficLastDay && station.trafficLastDay != "{}") {
                    const trafficData = JSON.parse(station.trafficLastDay);

                    const lteTotal = (trafficData.mobile_send + trafficData.mobile_received) / 1024;
                    const uidTotal = (trafficData.uid_send + trafficData.uid_received) / 1024;
                    const overallTotal = (trafficData.total_send + trafficData.total_received) / 1024;

                    const trafficCell = document.createElement('td');
                    trafficCell.textContent = `LTE: ${lteTotal.toFixed(1)}kb, UID: ${uidTotal.toFixed(1)}kb, Total: ${overallTotal.toFixed(1)}kb`;
                    row.appendChild(trafficCell);
                } else {
                    const trafficCell = document.createElement('td');
                    trafficCell.textContent = 'No data';
                    row.appendChild(trafficCell);
                }

                table.appendChild(row);
            });

        })
        .catch(error => console.error('Error fetching station list:', error));
});
