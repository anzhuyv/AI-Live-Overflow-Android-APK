const pet = document.getElementById('pet');
const bubble = document.getElementById('bubble');
const bubbleText = document.getElementById('bubbleText');

const tapLines = [
    '你戳我干嘛~',
    '我在呢',
    '喵？',
    '戳一下又不会变强',
    '别戳了啦'
];
const doubleTapLines = [
    '呀！',
    '双击是犯规的！',
    '我跳起来了~'
];
const longPressLines = [
    '脸好热...',
    '长按会害羞的...',
    '别这样盯着我...'
];
const screenshotLines = [
    '要把我拍好看点哦',
    '茄子~',
    '截图里有我！'
];
const appLines = {
    'com.android.chrome': '又在上网冲浪啦？',
    'com.tencent.mm': '微信消息多不多？',
    'com.tencent.mobileqq': 'QQ 响了吗？',
    'com.netease.cloudmusic': '听歌也带着我呀',
    'com.bilibili.app.in': '看 B 站不带我！',
    'tv.danmaku.bili': '看 B 站不带我！',
    'com.taobao.taobao': '买东西要审批一下',
    'com.jingdong.app.mall': '京东也审批一下',
    'com.ss.android.ugc.aweme': '抖音！哼！',
    'com.smile.gifmaker': '快手！哼！'
};

let idleTimer = null;
let bubbleTimer = null;

function showBubble(text, duration = 2500) {
    bubbleText.textContent = text;
    bubble.classList.add('show');
    clearTimeout(bubbleTimer);
    bubbleTimer = setTimeout(() => {
        bubble.classList.remove('show');
    }, duration);
}

function random(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function resetIdle() {
    clearTimeout(idleTimer);
    pet.classList.remove('shy');
    idleTimer = setTimeout(() => {
        showBubble('你还在吗？', 3000);
    }, 30000);
}

window.petEngine = {
    onTap: function (count) {
        showBubble(random(tapLines));
        resetIdle();
    },
    onDoubleTap: function () {
        showBubble(random(doubleTapLines));
        pet.classList.add('jump');
        setTimeout(() => pet.classList.remove('jump'), 500);
        resetIdle();
    },
    onLongPress: function () {
        showBubble(random(longPressLines));
        pet.classList.add('shy');
        resetIdle();
    },
    onScreenshot: function () {
        showBubble(random(screenshotLines));
        resetIdle();
    },
    onAppChanged: function (pkg) {
        const text = appLines[pkg] || null;
        if (text) showBubble(text);
        resetIdle();
    },
    onPower: function (connected) {
        showBubble(connected ? '充电中，暖暖的' : '拔电了...');
    },
    onBatteryLow: function () {
        showBubble('电量不足，我要睡着了...');
    }
};

resetIdle();
