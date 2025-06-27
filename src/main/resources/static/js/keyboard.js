let currentInput = null;

function renderKeyboard() {
    const kb = document.getElementById('keyboard');
    kb.innerHTML = '';

    // Новый макет клавиатуры — массив строк
    const layout = [
        ['1','2','3'],
        ['4','5','6'],
        ['7','8','9'],
        ['0','⌫']
    ];

    layout.forEach(row => {
        const rowDiv = document.createElement('div');
        rowDiv.className = 'keyboard-row'; // добавим для стилизации

        row.forEach(char => {
            const key = document.createElement('div');
            key.className = 'key';
            key.innerText = char;

            if (char === '⌫') {
                key.classList.add('key-wide'); // Шире обычной кнопки
            }

            key.onclick = () => {
                if (currentInput) {
                    const currentValue = currentInput.value.replace(/\D/g, ''); // Удаляем все не-цифры

                    if (char === '⌫') {
                        const newValue = currentValue.slice(0, -1);
                        currentInput.value = formatSnils(newValue);
                    } else if (currentValue.length < 11) {
                        const newValue = currentValue + char;
                        currentInput.value = formatSnils(newValue);
                    }
                }
            };
            rowDiv.appendChild(key);
        });

        kb.appendChild(rowDiv);
    });
}

function formatSnils(rawValue) {
    let formatted = '';

    // Форматируем по маске XXX-XXX-XXX XX
    for (let i = 0; i < rawValue.length; i++) {
        if (i === 3 || i === 6) {
            formatted += '-';
        } else if (i === 9) {
            formatted += ' ';
        }
        formatted += rawValue[i];
    }

    return formatted;
}

function clearField(id) {
    document.getElementById(id).value = '';
}

window.onload = function () {
    const snilsInput = document.getElementById('snilsInput');
    currentInput = snilsInput;
    renderKeyboard();

    // Обработчик для формы - очищаем маску перед отправкой
    document.getElementById('loginForm').onsubmit = function() {
        snilsInput.value = snilsInput.value.replace(/\D/g, ''); // Удаляем все не-цифры
        return snilsInput.value.length === 11; // Можно добавить валидацию
    };
};