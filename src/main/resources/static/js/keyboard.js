let currentInput = null;
let currentFormat = 'snils'; // По умолчанию СНИЛС

function renderKeyboard() {
    const kb = document.getElementById('keyboard');
    kb.innerHTML = '';

    const layout = [
        ['1','2','3'],
        ['4','5','6'],
        ['7','8','9'],
        ['0','⌫']
    ];

    layout.forEach(row => {
        const rowDiv = document.createElement('div');
        rowDiv.className = 'keyboard-row';

        row.forEach(char => {
            const key = document.createElement('div');
            key.className = 'key';
            key.innerText = char;

            if (char === '⌫') {
                key.classList.add('key-wide');
            }

            key.onclick = () => handleKeyPress(char);
            rowDiv.appendChild(key);
        });

        kb.appendChild(rowDiv);
    });
}

function handleKeyPress(char) {
    if (!currentInput) return;

    const currentValue = currentInput.value.replace(/\D/g, ''); // Удаляем все не-цифры

    if (char === '⌫') {
        const newValue = currentValue.slice(0, -1);
        currentInput.value = formatInput(newValue, currentFormat);
    } else {
        let maxDigits;
        switch(currentFormat) {
            case 'snils': maxDigits = 11; break;  // СНИЛС - 11 цифр
            case 'policy': maxDigits = 16; break;  // Полис - 16 цифр
            case 'card': maxDigits = 7; break;     // Карта - 7 цифр
            default: maxDigits = 20;
        }

        if (currentValue.length < maxDigits) {
            const newValue = currentValue + char;
            currentInput.value = formatInput(newValue, currentFormat);
        }
    }
}

function formatInput(rawValue, formatType) {
    switch(formatType) {
        case 'snils':
            // Форматирование СНИЛС: XXX-XXX-XXX XX (11 цифр)
            return formatSnils(rawValue);
        case 'policy':
            // Форматирование полиса: XXXX XXXX XXXX XXXX (16 цифр)
            return formatPolicyNumber(rawValue);
        case 'card':
            // Форматирование карты: XXXXXXX (7 цифр)
            return rawValue; // Без форматирования
        default:
            return rawValue;
    }
}

function formatSnils(value) {
    let result = '';
    for (let i = 0; i < value.length; i++) {
        if (i === 3 || i === 6) {
            result += '-';
        } else if (i === 9) {
            result += ' ';
        }
        result += value[i];
    }
    return result;
}

function formatPolicyNumber(value) {
    let result = '';
    for (let i = 0; i < value.length; i++) {
        if (i > 0 && i % 4 === 0) {
            result += ' ';
        }
        result += value[i];
    }
    return result;
}

window.onload = function () {
    const authInput = document.getElementById('authInput');
    const errorDiv = document.querySelector('.error-message');
    currentInput = authInput;
    renderKeyboard();

    // Обработчик для формы - очищаем маску перед отправкой
    document.getElementById('loginForm').onsubmit = function(e) {
        // Предотвращаем стандартную отправку формы
        e.preventDefault();

        const rawValue = authInput.value.replace(/\D/g, ''); // Удаляем все не-цифры
        authInput.value = rawValue; // Сохраняем очищенное значение

        let isValid = false;
        let errorMessage = '';

        switch(currentFormat) {
            case 'snils':
                isValid = rawValue.length === 11;
                errorMessage = isValid ? '' : 'СНИЛС должен содержать 11 цифр';
                break;
            case 'policy':
                isValid = rawValue.length === 16;
                errorMessage = isValid ? '' : 'Номер полиса должен содержать 16 цифр';
                break;
            case 'card':
                isValid = rawValue.length >= 1 && rawValue.length <= 7;
                errorMessage = isValid ? '' : 'Номер карты должен содержать от 1 до 7 цифр';
                break;
            default:
                isValid = false;
                errorMessage = 'Неизвестный метод авторизации';
        }

        // Обновляем сообщение об ошибке
        if (errorDiv) {
            errorDiv.textContent = errorMessage;
            errorDiv.style.display = errorMessage ? 'block' : 'none';
        }

        // Если валидация прошла, отправляем форму
        if (isValid) {
            this.submit();
        }
    };
};