/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
(function () {
    const urlNodeId = window.location.pathname.split("/")[2] || "";
    let NODE_ID = "";
    const MAX_POINTS = 60;

    const cpu = { time: [], value: [] };
    const mem = { time: [], value: [], total: [] };
    const net = { time: [], value: [], tx: [] };
    let charts = {};
    let loadTimer = null;


    const DISK_PALETTE = ["#66CCFF", "#52C41A", "#FAAD14", "#722ED1", "#13C2C2", "#F5222D", "#2F54EB", "#EB2F96"];

    
    function lighten(hex, amount) {
        const num = parseInt(hex.slice(1), 16);
        let r = (num >> 16) & 255, g = (num >> 8) & 255, b = num & 255;
        r = Math.round(r + (255 - r) * amount);
        g = Math.round(g + (255 - g) * amount);
        b = Math.round(b + (255 - b) * amount);
        return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
    }

    function init() {
        const selector = document.getElementById("node-selector");
        const noNode = document.getElementById("no-node");
        const monitorBody = document.getElementById("monitor-body");

        window.app.loadNodes().then(function (nodes) {
            NODE_ID = urlNodeId || window.app.currentNode();
            if (urlNodeId) {
                window.app.setCurrentNode(urlNodeId);
            }
            if (selector) {
                selector.replaceChildren();
                nodes.forEach(function (n) {
                    const option = document.createElement("option");
                    option.value = n.id;
                    option.textContent = n.name;
                    option.selected = n.id === NODE_ID;
                    selector.appendChild(option);
                });
                if (nodes.length === 0) {
                    const option = document.createElement("option");
                    option.value = "";
                    option.textContent = "无节点";
                    selector.appendChild(option);
                }
                selector.addEventListener("change", function () {
                    if (selector.value) {
                        window.app.setCurrentNode(selector.value);
                        window.app.renderSidebarNode();
                        window.location.href = "/monitor";
                    }
                });
            }

            if (!NODE_ID || !nodes.some(function (n) { return n.id === NODE_ID; })) {
                if (noNode) noNode.style.display = "flex";
                if (monitorBody) monitorBody.style.display = "none";
                if (selector) selector.disabled = true;
                return;
            }

            if (typeof echarts !== "undefined") {
                initCharts();
                load();
                if (loadTimer) clearInterval(loadTimer);
                loadTimer = setInterval(load, 2000);
            }
        }).catch(function () {  });
    }

    function initCharts() {
        const commonAxis = {
            type: "category",
            boundaryGap: false,
            data: [],
            axisLine: { lineStyle: { color: "#8E8EA0" } },
            axisLabel: { fontSize: 11, color: "#8E8EA0" }
        };
        const commonY = {
            type: "value",
            axisLabel: { fontSize: 11, color: "#8E8EA0" }
        };

        charts.cpu = echarts.init(document.getElementById("chart-cpu"));
        charts.cpu.setOption({
            tooltip: { trigger: "axis" },
            grid: { left: 50, right: 20, top: 20, bottom: 30 },
            xAxis: commonAxis,
            yAxis: Object.assign({}, commonY, { max: 100 }),
            series: [{
                name: "CPU %", type: "line", smooth: true, showSymbol: false,
                lineStyle: { color: "#66CCFF", width: 2 },
                areaStyle: { color: "rgba(102, 204, 255, 0.15)" },
                data: []
            }]
        });

        charts.mem = echarts.init(document.getElementById("chart-mem"));
        charts.mem.setOption({
            tooltip: { trigger: "axis" },
            grid: { left: 60, right: 20, top: 20, bottom: 30 },
            xAxis: commonAxis,
            yAxis: commonY,
            series: [
                { name: "已用 (MB)", type: "line", smooth: true, showSymbol: false, lineStyle: { color: "#66CCFF", width: 2 }, data: [] },
                { name: "总计 (MB)", type: "line", smooth: true, showSymbol: false, lineStyle: { color: "#8E8EA0", width: 1, type: "dashed" }, data: [] }
            ]
        });

        charts.net = echarts.init(document.getElementById("chart-net"));
        charts.net.setOption({
            tooltip: { trigger: "axis" },
            grid: { left: 60, right: 20, top: 20, bottom: 30 },
            xAxis: commonAxis,
            yAxis: commonY,
            series: [
                { name: "下行", type: "line", smooth: true, showSymbol: false, lineStyle: { color: "#66CCFF", width: 2 }, areaStyle: { color: "rgba(102,204,255,0.1)" }, data: [] },
                { name: "上行", type: "line", smooth: true, showSymbol: false, lineStyle: { color: "#52C41A", width: 2 }, areaStyle: { color: "rgba(82,196,26,0.1)" }, data: [] }
            ]
        });

        charts.disk = echarts.init(document.getElementById("chart-disk"));
        charts.disk.setOption({
            tooltip: {
                trigger: "item",
                formatter: function (params) {
                    const d = params.data || {};
                    if (d.diskInfo) {
                        const total = window.app.formatSize(d.diskInfo.total);
                        const used = window.app.formatSize(d.diskInfo.used);
                        const free = window.app.formatSize(d.diskInfo.free);
                        const pct = d.diskInfo.total ? (d.diskInfo.used / d.diskInfo.total * 100).toFixed(1) : "0";
                        return escapeHtml(d.diskInfo.mount) + "<br/>已用: " + escapeHtml(used) + " / " + escapeHtml(total) + " (" + escapeHtml(pct) + "%)<br/>剩余: " + escapeHtml(free);
                    }
                    return escapeHtml(params.name) + "<br/>占总空间: " + escapeHtml(params.percent) + "%";
                }
            },
            series: [

                {
                    name: "磁盘占比",
                    type: "pie",
                    radius: ["15%", "32%"],
                    label: { fontSize: 11, color: "#8E8EA0", formatter: "{b}\n{d}%" },
                    labelLine: { length: 8, length2: 8 },
                    data: []
                },

                {
                    name: "磁盘用量",
                    type: "pie",
                    radius: ["40%", "70%"],
                    label: { show: false },
                    labelLine: { show: false },
                    data: []
                }
            ]
        });

        window.addEventListener("resize", function () {
            Object.values(charts).forEach(function (c) { c.resize(); });
        });
    }

    function pushSeries(series, time, value) {
        series.time.push(time);
        series.value.push(value);
        if (series.time.length > MAX_POINTS) {
            series.time.shift();
            series.value.shift();
        }
    }

    function formatRate(bps) {
        return window.app.formatSize(bps) + "/s";
    }

    function updateUI(data) {
        const now = new Date();
        const t = String(now.getHours()).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0") + ":" + String(now.getSeconds()).padStart(2, "0");


        setText("stat-cpu", (data.cpuUsage != null ? data.cpuUsage : 0).toFixed(1) + "%");
        const loadEl = document.getElementById("stat-load");
        if (loadEl) {
            if (data.loadAverage != null && data.loadAverage >= 0) {
                loadEl.style.display = "";
                loadEl.textContent = "负载 " + data.loadAverage.toFixed(2);
            } else {
                loadEl.style.display = "none";
            }
        }
        setText("stat-mem", window.app.formatSize(data.memoryUsed) + " / " + window.app.formatSize(data.memoryTotal));
        const memPct = data.memoryTotal ? (data.memoryUsed / data.memoryTotal * 100).toFixed(1) : "--";
        setText("stat-mem-pct", "占用 " + memPct + "%");
        setText("stat-rx", formatRate(data.networkRxRate || 0));
        setText("stat-rx-total", window.app.formatSize(data.networkRxTotal));
        setText("stat-tx", formatRate(data.networkTxRate || 0));
        setText("stat-tx-total", window.app.formatSize(data.networkTxTotal));


        pushSeries(cpu, t, data.cpuUsage != null ? +data.cpuUsage.toFixed(1) : 0);
        charts.cpu.setOption({ xAxis: { data: cpu.time }, series: [{ data: cpu.value }] });

        pushSeries(mem, t, data.memoryUsed ? +(data.memoryUsed / 1048576).toFixed(1) : 0);
        mem.total.push(data.memoryTotal ? +(data.memoryTotal / 1048576).toFixed(1) : 0);
        if (mem.total.length > MAX_POINTS) mem.total.shift();
        charts.mem.setOption({ xAxis: { data: mem.time }, series: [{ data: mem.value }, { data: mem.total }] });

        pushSeries(net, t, data.networkRxRate ? +data.networkRxRate.toFixed(0) : 0);
        net.tx.push(data.networkTxRate ? +data.networkTxRate.toFixed(0) : 0);
        if (net.tx.length > MAX_POINTS) net.tx.shift();
        charts.net.setOption({ xAxis: { data: net.time }, series: [{ data: net.value }, { data: net.tx }] });


        if (data.disks && data.disks.length) {
            const innerData = data.disks.map(function (d, i) {
                return {
                    name: d.mount,
                    value: d.total,
                    itemStyle: { color: DISK_PALETTE[i % DISK_PALETTE.length] }
                };
            });
            const outerData = [];
            data.disks.forEach(function (d, i) {
                const c = DISK_PALETTE[i % DISK_PALETTE.length];
                const used = Math.max(0, d.total - d.free);
                const diskInfo = { mount: d.mount, total: d.total, used: used, free: d.free };
                outerData.push({ name: d.mount + " (已用)", value: used, itemStyle: { color: c }, diskInfo: diskInfo });
                outerData.push({ name: d.mount + " (剩余)", value: d.free, itemStyle: { color: lighten(c, 0.65) }, diskInfo: diskInfo });
            });
            charts.disk.setOption({
                series: [{ data: innerData }, { data: outerData }]
            });
        }
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    function load() {
        API.get("/nodes/" + NODE_ID + "/monitor")
            .then(function (data) {
                if (data && data.cpuUsage != null) updateUI(data);
            })
            .catch(function (e) {
                console.error("监控获取失败", e);
            });
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    init();
})();
