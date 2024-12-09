let previousData = null;

document.addEventListener('DOMContentLoaded', function() {
    const currentHost = window.location.origin;
    const stId = getLastSegment(window.location.href); // Получение stId из URL
    const apiUrl = `${currentHost}/api/stationInfo?stId=${stId}`;

    function fetchData() {
        fetch(apiUrl)
            .then(response => response.json())
            .then(data => {
                if (JSON.stringify(data) !== JSON.stringify(previousData)) {
                    previousData = data;
                    updateTable(data);
                }
            })
            .catch(error => console.error('Error fetching station info:', error));
    }

    function updateTable(data) {
        const table = document.getElementById('station_table');
        table.innerHTML = ''; // Очищаем таблицу
        const size = data.size;
        const state = data.state || {};

        for (let i = 1; i <= size; i++) {
            const row = document.createElement('tr');

            // Добавляем столбец с порядковым номером строки
            const indexCell = document.createElement('td');
            indexCell.textContent = i;
            row.appendChild(indexCell);

            // Проверка наличия данных в объекте state
            const stateData = state[i];

            const bankIdCell = document.createElement('td');
            bankIdCell.textContent = stateData ? stateData.bankId : '';
            row.appendChild(bankIdCell);

            const chargeCell = document.createElement('td');
            chargeCell.textContent = stateData ? stateData.charge : '';
            row.appendChild(chargeCell);

            const statusCell = document.createElement('td');
            statusCell.textContent = stateData ? 'Доступен' : '';
            row.appendChild(statusCell);

            const actionsCell = document.createElement('td');
            const openButton = document.createElement('button');
            openButton.textContent = 'Открыть';
            openButton.onclick = () => releaseSlot(currentHost, stId, i);
            actionsCell.appendChild(openButton);

            const forceButton = document.createElement('button');
            forceButton.textContent = 'Форс';
            forceButton.onclick = () => alert(`Форс ${stateData ? stateData.bankId : data.stId}`);
            actionsCell.appendChild(forceButton);

            const blockButton = document.createElement('button');
            blockButton.textContent = 'Заблокировать';
            blockButton.onclick = () => alert(`Заблокировать ${stateData ? stateData.bankId : data.stId}`);
            actionsCell.appendChild(blockButton);

            if (stateData) {
                row.appendChild(actionsCell);
            }

            table.appendChild(row);
        }
    }

    // Первичный вызов для загрузки данных
    fetchData();

    // Периодический вызов каждые 5 секунд
    setInterval(fetchData, 1000);
});

function getLastSegment(url) {
    const urlWithoutParams = url.split('?')[0]; // Убираем параметры
    const parts = urlWithoutParams.split('/');
    return parts.pop() || parts.pop();  // Учитываем случай, если URL оканчивается на '/'
}

function releaseSlot(currentHost, stId, num) {
    const apiUrl = `${currentHost}/api/release?stId=${stId}&num=${num}`;
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
//            alert(`Слот ${num} открыт: ${data.status}`);
        })
        .catch(error => console.error('Error releasing slot:', error));
}
