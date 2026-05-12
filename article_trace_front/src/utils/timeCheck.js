const timeConfig = {
    default: {
        message: "欢迎回来！"
    },
    timeRange: [{start: 0, end: 4, message: "夜深了，"},
        {start: 5, end: 8, message: "早上好，"},
        {start: 9, end: 11, message: "上午好 ，"},
        {start: 12, end: 14, message: "中午好 ，"},
        {start: 15, end: 18, message: "下午好 ，"},
        {start: 19, end: 23, message: "晚上好 ，"}]
}

export function checkTime() {
    const nowHour = new Date().getHours();
    for (const range of timeConfig.timeRange) {
        if (range.start <= nowHour && nowHour <= range.end) {
            return range.message
        }
    }
    return timeConfig.default.message;
}