let previousData = null;

let isFocused = true

document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'visible') {
        // Страница в фокусе, можно обновлять информацию
        console.log('Начинаем обновление информации');
        isFocused = true
    } else {
        // Страница не в фокусе, остановить обновление информации
        console.log('Останавливаем обновление информации');
        isFocused = false
    }
});

function formatTimestamp(timestamp) {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    day: '2-digit',
    month: '2-digit'
  }).format(new Date(timestamp));
}



document.addEventListener('DOMContentLoaded', function() {
    const currentHost = window.location.origin;
    const stId = getLastSegment(window.location.href); // Получение stId из URL
    const apiUrl = `${currentHost}/api/stationInfo?stId=${stId}`;

    function fetchData() {
        if (isFocused)
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
        const size = parseInt(data.size, 10);
        const state = data.state || {};

        const layout_top = document.getElementById('layout_top');
        const station_title = document.getElementById('station_name');
        station_title.textContent = `Станция ${data.stId}, онлайн: ${formatTimestamp(data.timestamp * 1000)}`

        const getLogButton = document.getElementById('stations_log_button');
        getLogButton.onclick = () => getLogs(currentHost, stId);
        const form = document.getElementById('uploadForm');
        form.action = `/api/upload/${stId}`;

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
            openButton.className = 'btn btn-primary';
            openButton.textContent = 'Открыть';
            openButton.onclick = () => releaseSlot(currentHost, stId, i);
            actionsCell.appendChild(openButton);

            const forceButton = document.createElement('button');
            forceButton.textContent = 'Форс';
            forceButton.className = 'btn btn-primary';
            forceButton.onclick = () => alert(`Форс ${stateData ? stateData.bankId : data.stId}`);
            actionsCell.appendChild(forceButton);

            const blockButton = document.createElement('button');
            blockButton.textContent = 'Заблокировать';
            blockButton.className = 'btn btn-primary';
            blockButton.onclick = () => alert(`Заблокировать ${stateData ? stateData.bankId : data.stId}`);
            actionsCell.appendChild(blockButton);



            if (stateData) {
                row.appendChild(actionsCell);
            }

            table.appendChild(row);
        }

        // Добавляем новые карточки на страницу
        const layoutBottom = document.getElementById('layout_bottom');
        layoutBottom.innerHTML = ''; // Очистка перед добавлением новых элементов

        if (data.events && Array.isArray(data.events)) {

            data.events.sort((a, b) => b.date - a.date);
            data.events.forEach(event => {
                event.date = formatTimestamp(event.date);
                const cardDiv = document.createElement('div');
                cardDiv.className = 'card mb-4';

                const cardBodyDiv = document.createElement('div');
                cardBodyDiv.className = 'card-body';
                cardBodyDiv.innerHTML = JSON.stringify(event, null, 2);

                cardDiv.appendChild(cardBodyDiv);
                layoutBottom.appendChild(cardDiv);
            });
        }


    }

    // Первичный вызов для загрузки данных
    fetchData();

    // Периодический вызов каждые 5 секунд
    setInterval(fetchData, 10000);
});

function getLastSegment(url) {
    const urlWithoutParams = url.split('?')[0]; // Убираем параметры
    const parts = urlWithoutParams.split('/');
    return parts.pop() || parts.pop();  // Учитываем случай, если URL оканчивается на '/'
}

function getLogs(currentHost, stId) {
    const apiUrl = `${currentHost}/api/getLogs?stId=${stId}`;
    fetch(apiUrl)
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.blob();
        })
        .then(blob => {
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = `logs_${stId}.zip`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            console.log('Logs successfully downloaded.');
        })
        .catch(error => console.error('Error getting logs:', error));
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
