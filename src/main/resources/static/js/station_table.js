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

//                const idCell = document.createElement('td');
//                idCell.textContent = station.id;
//                row.appendChild(idCell);

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
                // преобразуем таймстамп в миллисекунды
                const formattedDate = date.toLocaleString('ru-RU', {
                    day: '2-digit',
                    month: '2-digit',
                    year: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });
                timestampCell.textContent = formattedDate;
                row.appendChild(timestampCell);

                table.appendChild(row);
            });

        })
        .catch(error => console.error('Error fetching station list:', error));
});
