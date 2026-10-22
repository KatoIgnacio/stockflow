import { dateKey, money, number } from "./utils.js";

export function activityChart(orders, days) {
  const end = new Date(),
    dates = Array.from({ length: days }, (_, i) => {
      const d = new Date(end);
      d.setDate(d.getDate() - days + 1 + i);
      return d;
    });
  const values = dates.map(
    (d) =>
      orders
        .filter((o) => o.fecha.slice(0, 10) === dateKey(d))
        .reduce((s, o) => s + Math.round(o.total * 100), 0) / 100,
  );
  const top = Math.max(...values, 1000),
    width = 600,
    height = 175,
    left = 58,
    right = 12,
    bottom = 23,
    plotHeight = 135;
  const x = (i) => left + (i * (width - left - right)) / (days - 1),
    y = (v) => plotHeight + 5 - (v / top) * plotHeight;
  const points = values.map((v, i) => `${x(i)},${y(v)}`).join(" ");
  const labels = [
    0,
    Math.floor((days - 1) / 3),
    Math.floor(((days - 1) * 2) / 3),
    days - 1,
  ];
  const ticks = [0, 0.5, 1]
    .map(
      (f) =>
        `<line class="chart-grid" x1="${left}" x2="${width - right}" y1="${y(top * f)}" y2="${y(top * f)}"/><text class="chart-label" x="${left - 10}" y="${y(top * f) + 3}" text-anchor="end">${f === 0 ? "$0" : money(top * f >= 1000 ? Math.round((top * f) / 1000) : top * f) + (top * f >= 1000 ? " mil" : "")}</text>`,
    )
    .join("");
  return `<svg class="line-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="Monto de pedidos en los últimos ${days} días: ${money(values.reduce((a, b) => a + b, 0))}"><defs><linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1"><stop stop-color="var(--primary)" stop-opacity=".17"/><stop offset="1" stop-color="var(--primary)" stop-opacity="0"/></linearGradient></defs>${ticks}<polygon class="chart-area" points="${left},${y(0)} ${points} ${x(days - 1)},${y(0)}"/><polyline class="chart-line" points="${points}"/>${values.map((v, i) => (v ? `<circle cx="${x(i)}" cy="${y(v)}" r="3" fill="var(--primary)" stroke="var(--surface)" stroke-width="2"><title>${dateKey(dates[i])}: ${money(v)}</title></circle>` : "")).join("")}${labels.map((i) => `<text class="chart-label" x="${x(i)}" y="${height - bottom + 17}" text-anchor="middle">${dates[i].toLocaleDateString("es-CL", { day: "numeric", month: "short" })}</text>`).join("")}</svg>`;
}

export function inventoryDonut(products) {
  const counts = [
    products.filter(
      (p) => p.stockDisponible >= p.stockMinimo && p.stockDisponible > 0,
    ).length,
    products.filter(
      (p) => p.stockDisponible > 0 && p.stockDisponible < p.stockMinimo,
    ).length,
    products.filter((p) => p.stockDisponible === 0).length,
  ];
  const colors = ["var(--primary)", "#e3b867", "#e18b83"],
    total = products.length,
    circumference = 2 * Math.PI * 62;
  let offset = 0;
  return `<svg class="donut" viewBox="0 0 170 170" role="img" aria-label="${total} productos: ${counts[0]} disponibles, ${counts[1]} con stock bajo y ${counts[2]} agotados"><circle class="donut-track" cx="85" cy="85" r="62" fill="none" stroke-width="13"/>${counts
    .map((count, i) => {
      const len = total ? (count / total) * circumference : 0;
      const result = `<circle cx="85" cy="85" r="62" fill="none" stroke="${colors[i]}" stroke-width="13" stroke-dasharray="${Math.max(0, len - (len > 4 ? 3 : 0))} ${circumference}" stroke-dashoffset="${-offset}" transform="rotate(-90 85 85)"/>`;
      offset += len;
      return result;
    })
    .join(
      "",
    )}<text class="donut-number" x="85" y="84" text-anchor="middle">${number(total)}</text><text class="donut-label" x="85" y="102" text-anchor="middle">productos activos</text></svg>`;
}
