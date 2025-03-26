const pageSize = 20; // Количество станций на странице
let currentPage = 1; // Текущая страница
const DEFAULT_ONLINE = 60 * 3

let currentFilter = ""

// Получаем элементы кнопок один раз
const totalValueElem = document.getElementById('total-value');
const onlineValueElem = document.getElementById('online-value');
const offlineValueElem = document.getElementById('offline-value');

// Сохраняем кнопки в переменные
const totalBtn = document.getElementById('total-button');
const onlineBtn = document.getElementById('online-button');
const offlineBtn = document.getElementById('offline-button');

// Обработчики кликов
totalBtn.onclick = function() {
    loadStations(1);  // Просто вызывает с пустым фильтром
};

onlineBtn.onclick = function() {
    loadStations(1, 'online');  // Добавляет фильтр для онлайн
};

offlineBtn.onclick = function() {
    loadStations(1, 'offline');  // Добавляет фильтр для оффлайн
};


document.addEventListener('DOMContentLoaded', function () {
    // Считываем текущую страницу из URL
    const url = new URL(window.location.href);
    const pageFromUrl = parseInt(url.pathname.split('/').pop(), 10);

    // Если в URL указана страница, используем её, иначе по умолчанию - 1
    currentPage = !isNaN(pageFromUrl) && pageFromUrl > 0 ? pageFromUrl : 1;

    // Загружаем данные для текущей страницы
    loadStations(currentPage);
});

function loadStations(page, filter = '') {
    const currentHost = window.location.origin;
    const apiUrl = `${currentHost}/webApi/stationList?page=${page}&pageSize=${pageSize}&filter=${filter}`;

    currentFilter = filter
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
        const { stations, total, online, filtred } = data; // Предполагаем, что API возвращает массив `stations` и общее количество `total`

        // Обновление значений в кнопках
        totalValueElem.textContent = total;
        onlineValueElem.textContent = online;
        offlineValueElem.textContent = total - online;

        const table = document.getElementById('station_table');
        table.innerHTML = ''; // Очистить таблицу перед загрузкой новых данных

        stations.forEach((station, index) => {
            const row = document.createElement('tr');

            row.setAttribute('data-index', index);
            row.setAttribute('onclick', `goToStation('${station.stId}')`);

            const stIdCell = document.createElement('td');
            stIdCell.textContent = station.stId;
            row.appendChild(stIdCell);

            const stStatusCell = document.createElement('td');
            stStatusCell.textContent = station.status;
            row.appendChild(stStatusCell);

            const sizeCell = document.createElement('td');
            sizeCell.textContent = station.size;
            row.appendChild(sizeCell);

            const availableCell = document.createElement('td');
            availableCell.textContent = station.available;
            row.appendChild(availableCell);

            const timestampCell = document.createElement('td');
            const date = new Date(station.timestamp * 1000);
            timestampCell.textContent = date.toLocaleString('ru-RU', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
            // Проверяем, меньше ли timestamp текущего времени минус DEFAULT_ONLINE
            const currentTime = Date.now() / 1000; // Текущее время в секундах
            if (station.timestamp > currentTime - DEFAULT_ONLINE) {
                timestampCell.style.color = 'green';  // Если меньше, то зелёный
                timestampCell.style.fontWeight = 'bold';  // Добавляем жирный текст
            } else {
                timestampCell.style.color = 'black';  // Иначе чёрный
                timestampCell.style.fontWeight = 'normal';  // Обычный текст
            }

            row.appendChild(timestampCell);

            const trafficCell = document.createElement('td');
            if (station.trafficLastDay && station.trafficLastDay !== "{}") {
                const trafficData = JSON.parse(station.trafficLastDay);
                const lteTotal = (trafficData.mobile_send + trafficData.mobile_received) / 1024;
                const uidTotal = (trafficData.uid_send + trafficData.uid_received) / 1024;
                const overallTotal = (trafficData.total_send + trafficData.total_received) / 1024;
                trafficCell.textContent = `LTE: ${lteTotal.toFixed(1)}kb, UID: ${uidTotal.toFixed(1)}kb, Total: ${overallTotal.toFixed(1)}kb`;
            } else {
                trafficCell.textContent = 'No data';
            }
            row.appendChild(trafficCell);

            const imageCell = document.createElement('td');

            // Добавление изображения
            if (station.wallpaper) {
                const img = document.createElement('img');
                img.src = `/stationApi/download?path=uploads/wallpapers/${station.wallpaper}`;
                img.alt = 'Wallpaper';
                img.style.width = '50px'; // Пример размера
                img.style.height = '50px'; // Пример размера
                imageCell.appendChild(img);
            }
            row.appendChild(imageCell);

            const QRCell = document.createElement('td');

            // Добавление QR
            if (station.QRCode) {
                QRCell.textContent = station.QRCode
            }
//            const getLogButton = document.createElement('button');
//            getLogButton.textContent = 'Логи';
//            getLogButton.onclick = () => getLogs(currentHost, station.stId);
//            actionsCell.appendChild(getLogButton);
            row.appendChild(QRCell);

            table.appendChild(row);
        });

        renderPagination(filtred);
    })
        .catch(error => console.error('Error fetching station list:', error));
}

function renderPagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    const paginationList = document.querySelector('.datatable-pagination-list');
    const infoText = document.querySelector('.datatable-info');
    paginationList.innerHTML = ''; // Очистить предыдущие кнопки

    // Вычисляем диапазон страниц для отображения
    const range = 5;
    let startPage = Math.max(1, currentPage - Math.floor(range / 2));
    let endPage = Math.min(totalPages, currentPage + Math.floor(range / 2));

    // Если находимся слишком близко к последней странице, смещаем диапазон
    if (endPage - startPage < range) {
        startPage = Math.max(1, endPage - range);
    }

    // Обновление текста "Showing X to Y of Z entries"
    const startIndex = (currentPage - 1) * pageSize + 1;
    const endIndex = Math.min(currentPage * pageSize, total);
    infoText.textContent = `Показано с ${startIndex} по ${endIndex} из ${total} записей`;


    // Добавление кнопки "Назад"
    const prevItem = document.createElement('li');
    prevItem.className = `datatable-pagination-list-item ${currentPage === 1 ? 'datatable-disabled' : ''}`;
    prevItem.innerHTML = `<a data-page="${currentPage - 1}" class="datatable-pagination-list-item-link">‹</a>`;
    if (currentPage > 1) {
        prevItem.addEventListener('click', () => changePage(currentPage - 1));
    }
    paginationList.appendChild(prevItem);

    // Генерация номеров страниц
    for (let i = startPage; i <= endPage; i++) {
        const pageItem = document.createElement('li');
        pageItem.className = `datatable-pagination-list-item ${currentPage === i ? 'datatable-active' : ''}`;
        pageItem.innerHTML = `<a data-page="${i}" class="datatable-pagination-list-item-link">${i}</a>`;
        if (currentPage !== i) {
            pageItem.addEventListener('click', () => changePage(i));
        }
        paginationList.appendChild(pageItem);
    }

    // Добавление кнопки "Вперёд"
    const nextItem = document.createElement('li');
    nextItem.className = `datatable-pagination-list-item ${currentPage === totalPages ? 'datatable-disabled' : ''}`;
    nextItem.innerHTML = `<a data-page="${currentPage + 1}" class="datatable-pagination-list-item-link">›</a>`;
    if (currentPage < totalPages) {
        nextItem.addEventListener('click', () => changePage(currentPage + 1));
    }
    paginationList.appendChild(nextItem);
}


function changePage(page) {
    currentPage = page;

    // Обновляем URL без перезагрузки страницы
    const newUrl = `/stations/${page}`;
    window.history.pushState({ page }, `Page ${page}`, newUrl);

    // Загружаем данные для новой страницы
    loadStations(page, currentFilter);
}


function getLogs(currentHost, stId) {
    const apiUrl = `${currentHost}/webApi/getLogs?stId=${stId}`;
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
//            alert(`Слот ${num} открыт: ${data.status}`);
                console.log('запрос логов: ${data}');
        })
        .catch(error => console.error('Error getting logs:', error));
}

function goToStation(stId) {
    window.location.href = `/station/${stId}`;
}
