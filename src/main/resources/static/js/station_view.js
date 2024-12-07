document.addEventListener('DOMContentLoaded', function() {
    const currentHost = window.location.origin;
    const stId = getLastSegment(window.location.href); // Получение stId из URL
    const apiUrl = `${currentHost}/api/stationInfo?stId=${stId}`;

    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            const table = document.getElementById('station_table');
            const size = data.size;
            const availableSlots = data.available;

            for (let i = 0; i < size; i++) {
                const row = document.createElement('tr');

                // Добавляем столбец с порядковым номером строки
                const indexCell = document.createElement('td');
                indexCell.textContent = i + 1;
                row.appendChild(indexCell);

                const bankIdCell = document.createElement('td');
                bankIdCell.textContent = i < availableSlots ? data.stId : '';
                row.appendChild(bankIdCell);

                const chargeCell = document.createElement('td');
                chargeCell.textContent = i < availableSlots ? 'Заряжен' : '';
                row.appendChild(chargeCell);

                const statusCell = document.createElement('td');
                statusCell.textContent = i < availableSlots ? 'Доступен' : '';
                row.appendChild(statusCell);

                const actionsCell = document.createElement('td');
                const openButton = document.createElement('button');
                openButton.textContent = 'Открыть';
                openButton.onclick = () => alert(`Открыть ${data.stId}`);
                actionsCell.appendChild(openButton);

                const forceButton = document.createElement('button');
                forceButton.textContent = 'Форс';
                forceButton.onclick = () => alert(`Форс ${data.stId}`);
                actionsCell.appendChild(forceButton);

                const blockButton = document.createElement('button');
                blockButton.textContent = 'Заблокировать';
                blockButton.onclick = () => alert(`Заблокировать ${data.stId}`);
                actionsCell.appendChild(blockButton);

                if (i < availableSlots) {
                    row.appendChild(actionsCell);
                }

                table.appendChild(row);
            }
        })
        .catch(error => console.error('Error fetching station info:', error));
});

function getLastSegment(url) {
    const urlWithoutParams = url.split('?')[0]; // Убираем параметры
    const parts = urlWithoutParams.split('/');
    return parts.pop() || parts.pop();  // Учитываем случай, если URL оканчивается на '/'
}
