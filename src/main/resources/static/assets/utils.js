export const escapeHtml = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
export const money = (value) =>
  new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);
export const number = (value) =>
  new Intl.NumberFormat("es-CL").format(Number(value) || 0);
export const date = (value, full = false) =>
  value
    ? new Intl.DateTimeFormat(
        "es-CL",
        full
          ? {
              day: "numeric",
              month: "long",
              year: "numeric",
              hour: "2-digit",
              minute: "2-digit",
            }
          : { day: "2-digit", month: "short", year: "numeric" },
      ).format(new Date(value))
    : "—";
export const dateKey = (value) => {
  const d = new Date(value);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
export const initials = (name) =>
  String(name || "Cliente")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
export const orderCode = (id) => `#SF-${String(id).padStart(4, "0")}`;
export const sumOrders = (orders) =>
  orders.reduce((sum, o) => sum + Math.round(Number(o.total) * 100), 0) / 100;
export const productStatus = (p) =>
  p.stockDisponible === 0
    ? "empty"
    : p.stockDisponible < p.stockMinimo
      ? "low"
      : "ok";
export const statusLabel = (status) =>
  ({ empty: "Agotado", low: "Stock bajo", ok: "Disponible" })[status];
export const sortRows = (rows, key, direction = "asc") =>
  [...rows].sort((a, b) => {
    const av = a[key],
      bv = b[key];
    const result =
      typeof av === "number"
        ? av - bv
        : String(av ?? "").localeCompare(String(bv ?? ""), "es", {
            numeric: true,
            sensitivity: "base",
          });
    return direction === "asc" ? result : -result;
  });
export const cartTotal = (items, products) =>
  items.reduce((sum, item) => {
    const p = products.find((p) => p.id === item.productoId);
    return sum + (p ? Math.round(Number(p.precio) * 100) * item.cantidad : 0);
  }, 0) / 100;
export function periodQuery(desde, hasta) {
  if (desde && hasta && desde > hasta)
    throw new Error(
      "La fecha inicial no puede ser posterior a la fecha final.",
    );
  const q = new URLSearchParams();
  if (desde) q.set("desde", desde);
  if (hasta) q.set("hasta", hasta);
  return q.toString();
}
export function downloadFile(filename, content, type) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const csv = (rows) =>
  "\uFEFF" +
  rows
    .map((row) =>
      row
        .map((value) => {
          const text = String(value ?? "");
          const safe = /^\s*[=+@-]/.test(text) ? `'${text}` : text;
          return `"${safe.replaceAll('"', '""')}"`;
        })
        .join(";"),
    )
    .join("\r\n");
