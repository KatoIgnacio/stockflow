import { api } from "./api.js";
import { icon } from "./icons.js";
import {
  escapeHtml as e,
  money,
  number,
  date,
  dateKey,
  initials,
  orderCode,
  sumOrders,
  productStatus,
  statusLabel,
  sortRows,
  cartTotal,
  periodQuery,
  downloadFile,
  csv,
} from "./utils.js";
import { activityChart, inventoryDonut } from "./charts.js";

const $ = (selector) => document.querySelector(selector);
const main = $("#main"),
  modal = $("#modal");
const sections = [
  ["resumen", "Resumen", "grid"],
  ["inventario", "Inventario", "box"],
  ["clientes", "Clientes", "users"],
  ["pedidos", "Pedidos", "bag"],
  ["reportes", "Reportes", "chart"],
];
const state = {
  products: [],
  clients: [],
  orders: [],
  low: [],
  loaded: false,
  error: "",
  busy: false,
  days: 7,
  report: null,
  reportKey: "",
  reportError: "",
  reportLoading: false,
};
const filters = {
  inventario: { nombre: "", codigo: "", tab: "all", sort: "nombre", page: 1 },
  clientes: { nombre: "", email: "", sort: "nombre", page: 1 },
  pedidos: { desde: "", hasta: "", cliente: "", page: 1 },
  reportes: { desde: "", hasta: "" },
};
let productResults = [],
  clientResults = [],
  orderResults = [],
  reportSequence = 0,
  cart = [],
  dialogKind = "",
  dialogData = null,
  submitting = false;
const pageSize = 8;
const route = () => {
  const parts = location.hash.replace(/^#\/?/, "").split("/");
  return {
    page: sections.some((s) => s[0] === parts[0]) ? parts[0] : "resumen",
    report: parts[1] === "pedidos" ? "pedidos" : "inventario",
  };
};
const query = (values) =>
  new URLSearchParams(
    Object.entries(values).filter(([, v]) => v !== "" && v != null),
  ).toString();
const actionButton = (action, label, ico, extra = "") =>
  `<button type="button" class="button ${extra}" data-action="${action}">${ico ? icon(ico, 15) : ""}${label}</button>`;
const rowAction = (action, id, label, ico, danger = false) =>
  `<button type="button" class="icon-button ${danger ? "danger" : ""}" data-action="${action}" data-id="${id}" title="${e(label)}" aria-label="${e(label)}">${icon(ico, 16)}</button>`;
const badge = (p) =>
  `<span class="badge ${productStatus(p)}">${statusLabel(productStatus(p))}</span>`;
const pending = () => '<span class="badge pending">Pendiente</span>';
const productCell = (p) =>
  `<div class="cell-product"><span class="product-icon">${icon("box", 17)}</span><div class="cell-primary">${e(p.nombre)}<span class="cell-sub">${e(p.codigo)}</span></div></div>`;
const clientCell = (c) =>
  `<div class="cell-product"><span class="avatar">${e(initials(c.nombre))}</span><div class="cell-primary">${e(c.nombre)}<span class="cell-sub">${e(c.email)}</span></div></div>`;
const empty = (ico, title, description, button = "") =>
  `<div class="empty-state"><div class="empty-icon">${icon(ico, 24)}</div><h3>${title}</h3><p>${description}</p>${button}</div>`;
const stat = (label, value, note, ico) =>
  `<div class="stat-card"><div class="stat-top"><span class="stat-label">${label}</span><span class="stat-icon">${icon(ico, 17)}</span></div><div class="stat-value">${value}</div><div class="stat-note">${note}</div></div>`;
const heading = (eyebrow, title, description, actions = "") =>
  `<div class="page-heading"><div><div class="eyebrow">${eyebrow}</div><h1>${title}</h1><p class="subtitle">${description}</p></div><div class="heading-actions">${actions}</div></div>`;
const alertBox = (message) =>
  `<div class="error-panel" role="alert">${icon("alert", 18)}<span>${e(message)}</span>${actionButton("refresh", "Reintentar", "refresh", "small")}</div>`;

function toast(message, error = false) {
  const t = document.createElement("div");
  t.className = `toast${error ? " error" : ""}`;
  t.innerHTML = `${icon(error ? "alert" : "circleCheck", 18)}<span>${e(message)}</span><button class="icon-button" aria-label="Cerrar notificación">${icon("close", 14)}</button>`;
  t.querySelector("button").onclick = () => t.remove();
  $("#toasts").append(t);
  setTimeout(() => t.remove(), error ? 10000 : 5500);
}
function updateTheme() {
  const dark = document.documentElement.dataset.theme === "dark";
  $("#theme-toggle").innerHTML = icon(dark ? "sun" : "moon", 18);
  $("#theme-toggle").setAttribute(
    "aria-label",
    dark ? "Activar tema claro" : "Activar tema oscuro",
  );
  $("#theme-toggle").title = dark ? "Tema claro" : "Tema oscuro";
  $('meta[name="theme-color"]').content = dark ? "#10171d" : "#f5f7f9";
}
function connection(ok) {
  $("#connection-dot").className =
    `connection-dot ${ok ? "online" : "offline"}`;
  $("#connection-label").textContent = ok
    ? "Sistema conectado"
    : "Sin conexión";
}
function closeMenu() {
  const hadFocus = $("#sidebar").contains(document.activeElement);
  $("#sidebar").classList.remove("open");
  document.body.classList.remove("menu-open");
  $("#sidebar-overlay").hidden = true;
  $("#menu-toggle").setAttribute("aria-expanded", "false");
  if (hadFocus && matchMedia("(max-width:700px)").matches)
    $("#menu-toggle").focus();
}
function navigation() {
  const page = route().page;
  $("#navigation").innerHTML = sections
    .map(
      ([id, label, ico]) =>
        `<a class="nav-item ${id === page ? "active" : ""}" href="#/${id}" ${id === page ? 'aria-current="page"' : ""}>${icon(ico, 18)}<span>${label}</span>${id === "inventario" && state.low.length ? `<span class="nav-count">${state.low.length}</span>` : ""}</a>`,
    )
    .join("");
  $("#breadcrumb-current").textContent = sections.find((s) => s[0] === page)[1];
  document.title = `${$("#breadcrumb-current").textContent} · StockFlow`;
}

async function loadData({ quiet = false } = {}) {
  if (state.busy) return;
  reportSequence++;
  state.reportLoading = false;
  state.reportError = "";
  state.busy = true;
  $("#refresh-button").disabled = true;
  if (!state.loaded && !quiet)
    main.innerHTML =
      '<div class="initial-loading"><span class="spinner"></span>Preparando tu espacio de trabajo…</div>';
  try {
    const pf = filters.inventario,
      cf = filters.clientes,
      of = filters.pedidos;
    const tasks = [
      api("/productos"),
      api("/clientes"),
      api("/pedidos"),
      api("/productos/stock-bajo"),
    ];
    if (pf.nombre || pf.codigo)
      tasks.push(
        api(`/productos?${query({ nombre: pf.nombre, codigo: pf.codigo })}`),
      );
    else tasks.push(null);
    if (cf.nombre || cf.email)
      tasks.push(
        api(`/clientes?${query({ nombre: cf.nombre, email: cf.email })}`),
      );
    else tasks.push(null);
    if (of.desde || of.hasta)
      tasks.push(api(`/pedidos?${periodQuery(of.desde, of.hasta)}`));
    else tasks.push(null);
    const [products, clients, orders, low, pr, cr, or] =
      await Promise.all(tasks);
    Object.assign(state, {
      products,
      clients,
      orders,
      low,
      loaded: true,
      error: "",
    });
    productResults = pr || products;
    clientResults = cr || clients;
    orderResults = or || orders;
    connection(true);
    $("#last-updated").textContent =
      `Actualizado a las ${new Date().toLocaleTimeString("es-CL", { hour: "2-digit", minute: "2-digit" })}`;
    state.reportKey = "";
    state.report = null;
  } catch (error) {
    state.error = error.message;
    connection(false);
    if (quiet) toast(error.message, true);
  } finally {
    state.busy = false;
    $("#refresh-button").disabled = false;
    render();
  }
}

function pagination(total, page, key) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  filters[key].page = Math.min(page, pages);
  const p = filters[key].page;
  return `<div class="table-footer"><span>${total ? `${(p - 1) * pageSize + 1}–${Math.min(p * pageSize, total)} de ${number(total)} registros` : "Sin registros"}</span><div class="pagination"><button class="icon-button" data-action="page" data-key="${key}" data-page="${p - 1}" ${p <= 1 ? "disabled" : ""} aria-label="Página anterior">${icon("chevronLeft", 15)}</button><span class="page-number">${p} / ${pages}</span><button class="icon-button" data-action="page" data-key="${key}" data-page="${p + 1}" ${p >= pages ? "disabled" : ""} aria-label="Página siguiente">${icon("chevronRight", 15)}</button></div></div>`;
}
function slicePage(rows, key) {
  const f = filters[key];
  f.page = Math.max(
    1,
    Math.min(f.page, Math.ceil(rows.length / pageSize) || 1),
  );
  return rows.slice((f.page - 1) * pageSize, f.page * pageSize);
}
function orderTable(orders, { compact = false } = {}) {
  return `<div class="table-wrap"><table><thead><tr><th>Pedido</th><th>Cliente</th><th>Fecha</th><th>Estado</th><th class="align-right">Total</th><th><span class="muted">Detalle</span></th></tr></thead><tbody>${orders.map((o) => `<tr><td><button class="text-link" data-action="order-detail" data-id="${o.id}">${orderCode(o.id)}</button></td><td class="cell-primary">${e(o.clienteNombre)}</td><td class="muted">${date(o.fecha)}</td><td>${pending()}</td><td class="align-right cell-primary">${money(o.total)}</td><td class="align-right">${rowAction("order-detail", o.id, `Ver pedido ${orderCode(o.id)}`, "arrow")}</td></tr>`).join("")}</tbody></table></div>`;
}

function renderDashboard() {
  const units = state.products.reduce((s, p) => s + p.stockDisponible, 0),
    today = state.orders.filter(
      (o) => o.fecha.slice(0, 10) === dateKey(new Date()),
    );
  const recent = [...state.orders]
    .sort((a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id)
    .slice(0, 5);
  const low = [...state.low].sort(
    (a, b) => a.stockDisponible - b.stockDisponible,
  );
  const healthy = state.products.filter(
      (p) => productStatus(p) === "ok",
    ).length,
    lowNonZero = state.products.filter(
      (p) => productStatus(p) === "low",
    ).length,
    zero = state.products.filter((p) => p.stockDisponible === 0).length;
  const cutoff = new Date();
  cutoff.setHours(0, 0, 0, 0);
  cutoff.setDate(cutoff.getDate() - state.days + 1);
  const period = state.orders.filter(
    (o) =>
      new Date(o.fecha) >= cutoff &&
      o.fecha.slice(0, 10) <= dateKey(new Date()),
  );
  return (
    heading(
      "VISTA GENERAL",
      "Todo bajo control.",
      "Una vista clara de tu inventario y de lo que sucede en tu negocio.",
      `<a class="button" href="#/reportes">${icon("chart", 15)}Ver reportes</a>${actionButton("new-order", "Nuevo pedido", "plus", "primary")}`,
    ) +
    `<div class="stats-grid">${stat("Productos activos", number(state.products.length), `${number(units)} unidades en inventario`, "box")}${stat("Pedidos registrados", number(state.orders.length), `<span class="positive">${number(today.length)} hoy</span> · historial completo`, "bag")}${stat("Monto de pedidos", money(sumOrders(state.orders)), "Total acumulado · CLP", "wallet")}${stat("Stock bajo", number(low.length), `<span class="${low.length ? "warning" : "positive"}">${low.length ? "Requieren reposición" : "Inventario al día"}</span>`, "alert")}</div>
  <div class="dashboard-grid"><section class="panel"><div class="panel-heading"><div><h2>Actividad de pedidos</h2><p>Monto registrado por día</p></div><div class="segment" aria-label="Período del gráfico">${[7, 30].map((d) => `<button data-action="chart-days" data-days="${d}" class="${state.days === d ? "active" : ""}" aria-pressed="${state.days === d}">${d} días</button>`).join("")}</div></div><div class="panel-body"><div class="chart-total">${money(sumOrders(period))}<small>últimos ${state.days} días</small></div><div class="chart-frame">${activityChart(state.orders, state.days)}</div><div class="chart-legend"><span class="legend-dot"></span>Monto de pedidos · CLP</div></div></section>
  <section class="panel"><div class="panel-heading"><div><h2>Salud del inventario</h2><p>Disponibilidad de tu catálogo</p></div>${icon("layers", 17)}</div><div class="donut-layout">${inventoryDonut(state.products)}</div><div class="panel-body inventory-legend"><div><span class="legend-dot"></span>Disponible<strong>${healthy}</strong></div><div><span class="legend-dot low"></span>Stock bajo<strong>${lowNonZero}</strong></div><div><span class="legend-dot empty"></span>Agotado<strong>${zero}</strong></div></div></section></div>
  <div class="dashboard-grid"><section class="panel"><div class="panel-heading"><div><h2>Últimos pedidos</h2><p>Los movimientos más recientes de tu operación</p></div><a class="text-link" href="#/pedidos">Ver todos ${icon("arrow", 13)}</a></div>${recent.length ? orderTable(recent, { compact: true }) : empty("bag", "Tu primer pedido empieza aquí", "Registra un pedido y sus productos se descontarán del inventario.", actionButton("new-order", "Crear pedido", "plus", "primary"))}</section>
  <section class="panel"><div class="panel-heading"><div><h2>Atención al stock</h2><p>Productos por debajo del mínimo</p></div><span class="badge ${low.length ? "low" : "ok"}">${low.length} productos</span></div>${
    low.length
      ? `<div class="stock-list">${low
          .slice(0, 4)
          .map(
            (p) =>
              `<a href="#/inventario" class="stock-item" data-action="product-detail" data-id="${p.id}"><span class="product-icon">${icon("box", 16)}</span><div class="stock-item-info"><strong>${e(p.nombre)}</strong><small>${e(p.codigo)}</small></div><div class="stock-item-count">${number(p.stockDisponible)}<small>mín. ${number(p.stockMinimo)}</small></div></a>`,
          )
          .join(
            "",
          )}</div><div class="panel-bottom"><button class="text-link" data-action="show-low">Revisar inventario ${icon("arrow", 13)}</button></div>`
      : empty(
          "circleCheck",
          "Todo en su lugar",
          "No hay productos con stock inferior al mínimo.",
        )
  }</section></div>
  <div class="callout"><span class="callout-icon">${icon("layers", 19)}</span><div><h3>Información conectada, decisiones más simples.</h3><p>Cada pedido actualiza el inventario automáticamente y conserva sus precios originales.</p></div><a class="text-link" href="#/reportes">Explorar reportes ${icon("arrow", 13)}</a></div>`
  );
}

function renderProducts() {
  const f = filters.inventario;
  let rows = productResults.filter((p) =>
    f.tab === "low"
      ? state.low.some((l) => l.id === p.id)
      : f.tab === "empty"
        ? p.stockDisponible === 0
        : true,
  );
  const [key, dir] = f.sort.split(":");
  rows = sortRows(rows, key, dir);
  const visible = slicePage(rows, "inventario");
  return (
    heading(
      "CATÁLOGO",
      "Inventario",
      "Gestiona tus productos y mantén las existencias al día.",
      actionButton("new-product", "Nuevo producto", "plus", "primary"),
    ) +
    `<div class="stats-grid">${stat("Productos activos", number(state.products.length), "Disponibles en el catálogo", "box")}${stat("Unidades disponibles", number(state.products.reduce((s, p) => s + p.stockDisponible, 0)), "Stock total del inventario", "layers")}${stat("Valor del inventario", money(state.products.reduce((s, p) => s + Math.round(p.precio * 100) * p.stockDisponible, 0) / 100), "Según precio de venta actual", "wallet")}${stat("Por reponer", number(state.low.length), "Stock inferior al mínimo definido", "alert")}</div>
  <section class="panel"><div class="filter-tabs" aria-label="Estado del stock">${[
    ["all", "Todos", state.products.length],
    ["low", "Stock bajo", state.low.length],
    [
      "empty",
      "Agotados",
      state.products.filter((p) => !p.stockDisponible).length,
    ],
  ]
    .map(
      ([id, label, n]) =>
        `<button data-action="stock-tab" data-tab="${id}" class="${f.tab === id ? "active" : ""}" aria-pressed="${f.tab === id}">${label}<span>${n}</span></button>`,
    )
    .join("")}</div>
  <div class="toolbar"><form data-form="product-filter" role="search"><label class="search-field">${icon("search", 16)}<input name="nombre" aria-label="Buscar producto por nombre" placeholder="Buscar por nombre…" value="${e(f.nombre)}" maxlength="150"></label><label class="search-field">${icon("search", 16)}<input name="codigo" aria-label="Filtrar por código" placeholder="Código del producto" value="${e(f.codigo)}" maxlength="60"></label><button class="button small" type="submit">Buscar</button>${f.nombre || f.codigo ? actionButton("clear-products", "Limpiar", "close", "small") : ""}</form><select class="sort-select" data-sort="inventario" aria-label="Ordenar productos">${[
    ["nombre", "Nombre: A–Z"],
    ["nombre:desc", "Nombre: Z–A"],
    ["stockDisponible", "Menor stock"],
    ["stockDisponible:desc", "Mayor stock"],
    ["precio", "Menor precio"],
    ["precio:desc", "Mayor precio"],
  ]
    .map(
      ([v, t]) =>
        `<option value="${v}" ${f.sort === v ? "selected" : ""}>${t}</option>`,
    )
    .join("")}</select></div>
  ${visible.length ? `<div class="table-wrap"><table><thead><tr><th>Producto</th><th class="align-right">Precio unitario</th><th class="align-right">Stock</th><th class="align-right">Mínimo</th><th>Estado</th><th class="align-right">Acciones</th></tr></thead><tbody>${visible.map((p) => `<tr><td>${productCell(p)}</td><td class="align-right cell-primary">${money(p.precio)}</td><td class="align-right cell-primary">${number(p.stockDisponible)}</td><td class="align-right muted">${number(p.stockMinimo)}</td><td>${badge(p)}</td><td><div class="row-actions">${rowAction("product-detail", p.id, `Ver ${p.nombre}`, "eye")}${rowAction("edit-product", p.id, `Editar ${p.nombre}`, "edit")}${rowAction("delete-product", p.id, `Desactivar ${p.nombre}`, "trash", true)}</div></td></tr>`).join("")}</tbody></table></div>` : empty("box", state.products.length ? "No encontramos productos" : "Construye tu catálogo", state.products.length ? "Prueba con otro nombre, código o estado de stock." : "Agrega tu primer producto para comenzar a gestionar el inventario.", state.products.length ? actionButton("clear-products", "Limpiar filtros", "refresh") : actionButton("new-product", "Nuevo producto", "plus", "primary"))}${pagination(rows.length, f.page, "inventario")}</section>`
  );
}

function renderClients() {
  const f = filters.clientes,
    rows = sortRows(clientResults, "nombre"),
    visible = slicePage(rows, "clientes");
  return (
    heading(
      "RELACIONES",
      "Clientes",
      "Toda la información de tus clientes, con su historial a un clic.",
      actionButton("new-client", "Nuevo cliente", "plus", "primary"),
    ) +
    `<section class="panel"><div class="panel-heading"><div><h2>Directorio de clientes <span class="badge neutral">${number(state.clients.length)}</span></h2><p>Clientes activos de tu negocio</p></div>${icon("users", 18)}</div><div class="toolbar"><form data-form="client-filter" role="search"><label class="search-field">${icon("search", 16)}<input name="nombre" aria-label="Buscar cliente por nombre" placeholder="Buscar por nombre…" value="${e(f.nombre)}" maxlength="120"></label><label class="search-field">${icon("mail", 16)}<input name="email" aria-label="Filtrar por correo" placeholder="Correo electrónico" value="${e(f.email)}" maxlength="180"></label><button class="button small" type="submit">Buscar</button>${f.nombre || f.email ? actionButton("clear-clients", "Limpiar", "close", "small") : ""}</form></div>
  ${
    visible.length
      ? `<div class="table-wrap"><table><thead><tr><th>Cliente</th><th>Pedidos</th><th class="align-right">Monto registrado</th><th>Estado</th><th class="align-right">Acciones</th></tr></thead><tbody>${visible
          .map((c) => {
            const orders = state.orders.filter((o) => o.clienteId === c.id);
            return `<tr><td>${clientCell(c)}</td><td>${orders.length} ${orders.length === 1 ? "pedido" : "pedidos"}</td><td class="align-right cell-primary">${money(sumOrders(orders))}</td><td><span class="badge">Activo</span></td><td><div class="row-actions">${rowAction("client-detail", c.id, `Ver historial de ${c.nombre}`, "eye")}${rowAction("edit-client", c.id, `Editar ${c.nombre}`, "edit")}${rowAction("delete-client", c.id, `Desactivar ${c.nombre}`, "trash", true)}</div></td></tr>`;
          })
          .join("")}</tbody></table></div>`
      : empty(
          "users",
          state.clients.length
            ? "No encontramos clientes"
            : "Tu próxima relación empieza aquí",
          state.clients.length
            ? "Prueba con otro nombre o correo electrónico."
            : "Registra un cliente para poder asociarle pedidos.",
          state.clients.length
            ? actionButton("clear-clients", "Limpiar filtros", "refresh")
            : actionButton("new-client", "Nuevo cliente", "plus", "primary"),
        )
  }${pagination(rows.length, f.page, "clientes")}</section>`
  );
}

function dateInputs(f, form) {
  return `<form class="date-filter" data-form="${form}"><label>Desde<input type="date" name="desde" aria-label="Fecha inicial" value="${e(f.desde)}"></label><label>Hasta<input type="date" name="hasta" aria-label="Fecha final" value="${e(f.hasta)}"></label><button class="button small" type="submit">Aplicar</button>${f.desde || f.hasta ? actionButton(form === "order-filter" ? "clear-orders" : "clear-report", "Todo el período", "close", "small") : ""}</form>`;
}
function renderOrders() {
  const f = filters.pedidos;
  let rows = [...orderResults].sort(
    (a, b) => b.fecha.localeCompare(a.fecha) || b.id - a.id,
  );
  if (f.cliente) rows = rows.filter((o) => o.clienteId === Number(f.cliente));
  const visible = slicePage(rows, "pedidos");
  const uniqueClients = [
    ...new Map(
      state.orders.map((o) => [
        o.clienteId,
        { id: o.clienteId, nombre: o.clienteNombre },
      ]),
    ).values(),
  ].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  return (
    heading(
      "OPERACIÓN",
      "Pedidos",
      "Registra ventas, consulta sus detalles y sigue cada movimiento.",
      actionButton("new-order", "Nuevo pedido", "plus", "primary"),
    ) +
    `<div class="stats-grid">${stat("Pedidos del período", number(rows.length), "Según los filtros seleccionados", "bag")}${stat("Monto del período", money(sumOrders(rows)), "Monto registrado en CLP", "wallet")}${stat("Unidades pedidas", number(rows.reduce((s, o) => s + o.items.reduce((n, i) => n + i.cantidad, 0), 0)), "Suma de todos los ítems del período", "layers")}${stat("Clientes del período", number(new Set(rows.map((o) => o.clienteId)).size), "Clientes con pedidos registrados", "users")}</div>
  <section class="panel"><div class="toolbar">${dateInputs(f, "order-filter")}<select class="sort-select" data-order-client aria-label="Filtrar pedidos por cliente"><option value="">Todos los clientes</option>${uniqueClients.map((c) => `<option value="${c.id}" ${Number(f.cliente) === c.id ? "selected" : ""}>${e(c.nombre)}</option>`).join("")}</select></div>${rows.length ? orderTable(visible) : empty("bag", state.orders.length ? "No hay pedidos en este período" : "Todo listo para tu primer pedido", state.orders.length ? "Amplía el rango de fechas o cambia el cliente." : "Selecciona un cliente y agrega productos con stock disponible.", actionButton("new-order", "Crear pedido", "plus", "primary"))}${pagination(rows.length, f.page, "pedidos")}</section><p class="subtitle">Los pedidos se registran como pendientes. El inventario se descuenta al confirmar su creación.</p>`
  );
}

function renderReports() {
  const type = route().report,
    f = filters.reportes,
    key = type === "pedidos" ? `${type}?${query(f)}` : type;
  let body = "";
  if (state.reportLoading || (state.reportKey !== key && !state.reportError)) {
    body =
      '<div class="initial-loading"><span class="spinner"></span>Generando reporte…</div>';
    if (!state.reportLoading) queueMicrotask(() => loadReport(type, key));
  } else if (state.reportError)
    body = empty(
      "alert",
      "No pudimos generar el reporte",
      e(state.reportError),
      actionButton("retry-report", "Reintentar", "refresh"),
    );
  else if (state.report) {
    const r = state.report;
    if (type === "inventario") {
      const ps = r.productos,
        units = ps.reduce((s, p) => s + p.stockDisponible, 0);
      body = `<div class="report-summary"><div><span>Productos activos</span><strong>${number(ps.length)}</strong></div><div><span>Unidades disponibles</span><strong>${number(units)}</strong></div><div><span>Valor a precio actual</span><strong>${money(ps.reduce((s, p) => s + Math.round(p.precio * 100) * p.stockDisponible, 0) / 100)}</strong></div></div>${ps.length ? `<div class="table-wrap"><table><thead><tr><th>Producto</th><th class="align-right">Precio</th><th class="align-right">Stock</th><th class="align-right">Mínimo</th><th>Estado</th><th class="align-right">Valor en stock</th></tr></thead><tbody>${ps.map((p) => `<tr><td>${productCell(p)}</td><td class="align-right">${money(p.precio)}</td><td class="align-right">${number(p.stockDisponible)}</td><td class="align-right muted">${number(p.stockMinimo)}</td><td>${badge(p)}</td><td class="align-right cell-primary">${money((Math.round(p.precio * 100) * p.stockDisponible) / 100)}</td></tr>`).join("")}</tbody></table></div>` : empty("box", "El inventario está vacío", "Los productos registrados aparecerán en este reporte.")}`;
    } else {
      body = `<div class="report-summary"><div><span>Pedidos del período</span><strong>${number(r.pedidos.length)}</strong></div><div><span>Monto total del servidor</span><strong>${money(r.montoTotal)}</strong></div><div><span>Clientes con pedidos</span><strong>${number(new Set(r.pedidos.map((o) => o.clienteId)).size)}</strong></div></div>${r.pedidos.length ? orderTable([...r.pedidos].sort((a, b) => b.fecha.localeCompare(a.fecha))) : empty("bag", "Sin pedidos en este período", "Prueba con un rango de fechas más amplio.")}`;
    }
    body += `<div class="report-generated">${icon("clock", 12)} Generado el ${date(r.generadoEn, true)} · Importes en pesos chilenos (CLP)</div>`;
  }
  return (
    heading(
      "INFORMACIÓN",
      "Reportes",
      "Consulta información actualizada y descarga los datos de tu operación.",
      `${actionButton("export-csv", "Descargar CSV", "download")}${actionButton("export-json", "Descargar JSON", "file", "primary")}`,
    ) +
    `<section class="panel"><div class="report-controls"><div class="report-tabs"><a href="#/reportes/inventario" class="${type === "inventario" ? "active" : ""}" ${type === "inventario" ? 'aria-current="page"' : ""}>Inventario</a><a href="#/reportes/pedidos" class="${type === "pedidos" ? "active" : ""}" ${type === "pedidos" ? 'aria-current="page"' : ""}>Pedidos</a></div>${type === "pedidos" ? dateInputs(f, "report-filter") : '<span class="muted" style="font-size:10px">Productos activos · Existencias actuales</span>'}</div>${body}</section><p class="subtitle">El CSV se puede abrir en Excel. El JSON conserva todos los detalles del reporte original.</p>`
  );
}
async function loadReport(type, key) {
  const seq = ++reportSequence;
  state.reportLoading = true;
  state.reportError = "";
  try {
    const q =
      type === "pedidos"
        ? periodQuery(filters.reportes.desde, filters.reportes.hasta)
        : "";
    const r = await api(`/reportes/${type}${q ? "?" + q : ""}`);
    if (seq !== reportSequence) return;
    state.report = r;
    state.reportKey = key;
  } catch (err) {
    if (seq !== reportSequence) return;
    state.reportError = err.message;
    state.reportKey = key;
    state.report = null;
  } finally {
    if (seq === reportSequence) {
      state.reportLoading = false;
      if (route().page === "reportes") render();
    }
  }
}

function render() {
  navigation();
  if (!state.loaded) {
    main.innerHTML = state.error
      ? heading(
          "ESPACIO DE TRABAJO",
          "Conectemos tu operación.",
          "Inicia la aplicación y vuelve a intentar.",
        ) + alertBox(state.error)
      : '<div class="initial-loading"><span class="spinner"></span>Cargando StockFlow…</div>';
    return;
  }
  const content = {
    resumen: renderDashboard,
    inventario: renderProducts,
    clientes: renderClients,
    pedidos: renderOrders,
    reportes: renderReports,
  };
  main.innerHTML =
    (state.error ? alertBox(state.error) : "") + content[route().page]();
}

function openModal(title, description, body, footer = "", wide = false) {
  modal.className = `modal${wide ? " wide" : ""}`;
  modal.innerHTML = `<div class="modal-heading"><div><h2 id="modal-title">${title}</h2>${description ? `<p>${description}</p>` : ""}</div><button class="icon-button" data-action="close-modal" aria-label="Cerrar ventana">${icon("close", 19)}</button></div>${body}${footer}`;
  if (!modal.open) modal.showModal();
  modal.scrollTop = 0;
  (
    modal.querySelector("form input, form select") ||
    modal.querySelector("button")
  ).focus({ preventScroll: true });
}
const formError = () =>
  '<div id="form-error" class="form-error" role="alert" tabindex="-1"></div>';
const formFooter = (label) =>
  `<div class="modal-footer">${actionButton("close-modal", "Cancelar", "")}<button class="button primary" type="submit" form="edit-form">${icon("check", 15)}${label}</button></div>`;
function showFormError(error) {
  const el = $("#form-error");
  if (el) {
    el.textContent = [error.message, ...(error.details || [])].join("\n");
    el.classList.add("visible");
    el.focus();
  } else toast(error.message, true);
}
function setSubmitting(value) {
  submitting = value;
  modal
    .querySelectorAll("button,input,select")
    .forEach((el) => (el.disabled = value));
}
function closeModal() {
  if (submitting) return;
  modal.close();
  dialogKind = "";
  dialogData = null;
  cart = [];
}

function productForm(p = null) {
  dialogKind = "product";
  dialogData = p;
  openModal(
    p ? "Editar producto" : "Nuevo producto",
    p
      ? "Actualiza los datos y existencias de este producto."
      : "Agrega un producto a tu inventario.",
    `<form id="edit-form" data-form="product" class="modal-body">${formError()}<div class="form-grid"><label class="field full">Nombre <input name="nombre" required maxlength="150" value="${e(p?.nombre || "")}" placeholder="Ej. Teclado mecánico" autocomplete="off"></label><label class="field full">Código único<input name="codigo" required maxlength="60" value="${e(p?.codigo || "")}" placeholder="Ej. TEC-001" autocomplete="off"><small>Utiliza un código que permita identificarlo fácilmente.</small></label><label class="field full">Precio unitario (CLP)<input type="number" name="precio" required min="0.01" max="9999999999.99" step="0.01" value="${p?.precio ?? ""}" placeholder="0" inputmode="decimal"></label><label class="field">Stock disponible<input type="number" name="stockDisponible" required min="0" max="2147483647" step="1" value="${p?.stockDisponible ?? 0}" inputmode="numeric"></label><label class="field">Stock mínimo<input type="number" name="stockMinimo" required min="0" max="2147483647" step="1" value="${p?.stockMinimo ?? 0}" inputmode="numeric"></label></div><div class="form-hint">${icon("info", 15)}<span>Se considera stock bajo cuando las existencias son inferiores al mínimo. Los cambios de precio no modifican pedidos anteriores.</span></div></form>`,
    formFooter(p ? "Guardar cambios" : "Crear producto"),
  );
}
function clientForm(c = null) {
  dialogKind = "client";
  dialogData = c;
  openModal(
    c ? "Editar cliente" : "Nuevo cliente",
    "Mantén los datos de contacto de tu cliente actualizados.",
    `<form id="edit-form" data-form="client" class="modal-body">${formError()}<div class="form-grid"><label class="field full">Nombre completo<input name="nombre" required maxlength="120" value="${e(c?.nombre || "")}" placeholder="Ej. Ana Torres" autocomplete="name"></label><label class="field full">Correo electrónico<input type="email" name="email" required maxlength="180" value="${e(c?.email || "")}" placeholder="nombre@ejemplo.cl" autocomplete="email"></label></div><div class="form-hint">${icon("info", 15)}<span>Cada correo identifica a un cliente. Su historial de pedidos se conserva aunque cambien sus datos.</span></div></form>`,
    formFooter(c ? "Guardar cambios" : "Crear cliente"),
  );
}

async function productDetail(id) {
  try {
    const p = await api(`/productos/${id}`);
    dialogKind = "product-detail";
    dialogData = p;
    openModal(
      "Detalle del producto",
      "Información actual del inventario.",
      `<div class="modal-body"><div class="detail-hero"><span class="product-icon">${icon("box", 25)}</span><div><h3>${e(p.nombre)}</h3><p>${e(p.codigo)}</p></div></div><dl class="detail-grid"><div><dt>Precio unitario</dt><dd>${money(p.precio)}</dd></div><div><dt>Estado del inventario</dt><dd>${badge(p)}</dd></div><div><dt>Stock disponible</dt><dd>${number(p.stockDisponible)} unidades</dd></div><div><dt>Stock mínimo</dt><dd>${number(p.stockMinimo)} unidades</dd></div></dl></div>`,
      `<div class="modal-footer">${actionButton("close-modal", "Cerrar", "")}${actionButton("edit-product", "Editar producto", "edit", "primary").replace('data-action="edit-product"', `data-action="edit-product" data-id="${p.id}"`)}</div>`,
    );
  } catch (err) {
    toast(err.message, true);
  }
}
async function clientDetail(id) {
  try {
    const orders = await api(`/clientes/${id}/pedidos`);
    let c = state.clients.find((c) => c.id === id);
    if (!c) {
      try {
        c = await api(`/clientes/${id}`);
      } catch (err) {
        if (err.status !== 404) throw err;
        c = {
          id,
          nombre: orders[0]?.clienteNombre || "Cliente inactivo",
          email: "Cliente desactivado",
          activo: false,
        };
      }
    }
    dialogKind = "client-detail";
    dialogData = c;
    openModal(
      "Historial del cliente",
      "Sus pedidos se conservan como parte de la operación.",
      `<div class="modal-body"><div class="detail-hero"><span class="avatar">${e(initials(c.nombre))}</span><div><h3>${e(c.nombre)}</h3><p>${e(c.email)}${c.activo === false ? " · Inactivo" : ""}</p></div></div><dl class="detail-grid"><div><dt>Pedidos registrados</dt><dd>${orders.length}</dd></div><div><dt>Monto acumulado</dt><dd>${money(sumOrders(orders))}</dd></div></dl><div class="detail-section-title"><h3>Historial de pedidos</h3><span class="badge neutral">${orders.length}</span></div>${orders.length ? orderTable([...orders].sort((a, b) => b.fecha.localeCompare(a.fecha))) : empty("bag", "Aún no hay pedidos", "Los pedidos de este cliente aparecerán aquí.")}</div>`,
      `<div class="modal-footer">${actionButton("close-modal", "Cerrar", "")}${c.activo !== false ? actionButton("client-new-order", "Crear pedido", "plus", "primary").replace('data-action="client-new-order"', `data-action="client-new-order" data-id="${c.id}"`) : ""}</div>`,
      true,
    );
  } catch (err) {
    toast(err.message, true);
  }
}
async function orderDetail(id) {
  try {
    const o = await api(`/pedidos/${id}`);
    dialogKind = "order-detail";
    dialogData = o;
    openModal(
      `Pedido ${orderCode(o.id)}`,
      date(o.fecha, true),
      `<div class="modal-body"><dl class="detail-grid"><div><dt>Cliente</dt><dd><button class="text-link" data-action="client-detail" data-id="${o.clienteId}">${e(o.clienteNombre)} ${icon("arrow", 12)}</button></dd></div><div><dt>Estado</dt><dd>${pending()}</dd></div></dl><div class="detail-section-title"><h3>Productos del pedido</h3><span class="muted" style="font-size:10px">Precios al momento de la compra</span></div><div class="table-wrap"><table><thead><tr><th>Producto</th><th class="align-right">Cantidad</th><th class="align-right">Precio unitario</th><th class="align-right">Subtotal</th></tr></thead><tbody>${o.items.map((i) => `<tr><td class="cell-primary">${e(i.nombre)}<span class="cell-sub">${e(i.codigo)}</span></td><td class="align-right">${number(i.cantidad)}</td><td class="align-right">${money(i.precioUnitario)}</td><td class="align-right cell-primary">${money(i.subtotal)}</td></tr>`).join("")}</tbody></table></div><div class="order-total"><span>Total del pedido · CLP</span><strong>${money(o.total)}</strong></div><div class="form-hint">${icon("circleCheck", 15)}<span>El stock de estos productos ya fue descontado al crear el pedido.</span></div></div>`,
      `<div class="modal-footer">${actionButton("close-modal", "Cerrar", "")}${actionButton("order-json", "Descargar detalle", "download")}</div>`,
      true,
    );
  } catch (err) {
    toast(err.message, true);
  }
}

function deleteDialog(kind, id) {
  const item = (kind === "product" ? state.products : state.clients).find(
    (i) => i.id === id,
  );
  if (!item) return;
  dialogKind = "delete";
  dialogData = { kind, item };
  openModal(
    kind === "product" ? "Desactivar producto" : "Desactivar cliente",
    "Este cambio conserva el historial de tu operación.",
    `<form id="edit-form" data-form="delete" class="modal-body">${formError()}<p class="confirmation-message">¿Quieres desactivar a <span class="confirmation-name">${e(item.nombre)}</span>? ${kind === "product" ? "Dejará de aparecer en el inventario y no podrá agregarse a nuevos pedidos." : "Dejará de aparecer en el directorio y no podrá recibir nuevos pedidos."} Los pedidos anteriores se conservarán.</p></form>`,
    `<div class="modal-footer">${actionButton("close-modal", "Cancelar", "")}<button class="button danger" type="submit" form="edit-form">${icon("trash", 15)}Desactivar</button></div>`,
  );
}

async function newOrder(clientId = "") {
  try {
    const [products, clients] = await Promise.all([
      api("/productos"),
      api("/clientes"),
    ]);
    state.products = products;
    state.clients = clients;
    cart = [];
    dialogKind = "order";
    dialogData = { clientId };
    if (!clients.length) {
      toast("Registra un cliente antes de crear un pedido.");
      clientForm();
      return;
    }
    if (!products.some((p) => p.stockDisponible > 0)) {
      toast("Necesitas al menos un producto con stock para crear un pedido.");
      location.hash = "#/inventario";
      closeModal();
      return;
    }
    openModal(
      "Nuevo pedido",
      "Selecciona un cliente y agrega los productos de su compra.",
      `<form id="edit-form" data-form="order" class="modal-body">${formError()}<label class="field">Cliente<select name="clienteId" required><option value="">Selecciona un cliente</option>${sortRows(
        clients,
        "nombre",
      )
        .map(
          (c) =>
            `<option value="${c.id}" ${c.id === Number(clientId) ? "selected" : ""}>${e(c.nombre)} · ${e(c.email)}</option>`,
        )
        .join(
          "",
        )}</select></label><div class="detail-section-title" style="margin-top:23px"><h3>Productos del pedido</h3><span class="muted" style="font-size:10px">Disponibilidad actual</span></div><div class="cart-add"><select id="cart-product" aria-label="Producto para agregar"><option value="">Selecciona un producto</option>${sortRows(
        products,
        "nombre",
      )
        .map(
          (p) =>
            `<option value="${p.id}" ${!p.stockDisponible ? "disabled" : ""}>${e(p.nombre)} · ${e(p.codigo)} · ${money(p.precio)} · ${p.stockDisponible ? "Stock: " + p.stockDisponible : "Agotado"}</option>`,
        )
        .join(
          "",
        )}</select>${actionButton("cart-add", "Agregar", "plus")}</div><div id="cart-content"></div><div class="order-total"><span>Total estimado · CLP</span><strong id="cart-total">${money(0)}</strong></div><div class="form-hint">${icon("info", 15)}<span>Al confirmar se valida el stock y se calcula el total en el servidor. Si falta stock, se rechaza el pedido completo sin descontar productos.</span></div></form>`,
      formFooter("Confirmar pedido"),
      true,
    );
    renderCart();
  } catch (err) {
    toast(err.message, true);
  }
}
function renderCart() {
  const target = $("#cart-content");
  if (!target) return;
  target.innerHTML = cart.length
    ? `<div class="table-wrap"><table class="cart-table"><thead><tr><th>Producto</th><th>Cantidad</th><th class="align-right">Subtotal</th><th>Quitar</th></tr></thead><tbody>${cart
        .map((item) => {
          const p = state.products.find((p) => p.id === item.productoId);
          return `<tr><td class="item-name cell-primary">${e(p.nombre)}<span class="cell-sub">${money(p.precio)} · ${number(p.stockDisponible)} disponibles</span></td><td><div class="quantity-control"><button type="button" class="icon-button" data-action="cart-minus" data-id="${p.id}" aria-label="Reducir cantidad de ${e(p.nombre)}" ${item.cantidad <= 1 ? "disabled" : ""}>${icon("minus", 12)}</button><input type="number" data-quantity="${p.id}" aria-label="Cantidad de ${e(p.nombre)}" value="${item.cantidad}" min="1" max="${p.stockDisponible}" step="1" required><button type="button" class="icon-button" data-action="cart-plus" data-id="${p.id}" aria-label="Aumentar cantidad de ${e(p.nombre)}" ${item.cantidad >= p.stockDisponible ? "disabled" : ""}>${icon("plus", 12)}</button></div>${item.cantidad > p.stockDisponible ? '<div class="cart-stock-error">Stock insuficiente</div>' : ""}</td><td class="align-right cell-primary">${money((Math.round(p.precio * 100) * item.cantidad) / 100)}</td><td>${rowAction("cart-remove", p.id, `Quitar ${p.nombre}`, "trash", true)}</td></tr>`;
        })
        .join("")}</tbody></table></div>`
    : '<div class="cart-empty">Agrega productos para comenzar a armar el pedido.</div>';
  $("#cart-total").textContent = money(cartTotal(cart, state.products));
}
function addCart() {
  const id = Number($("#cart-product").value),
    p = state.products.find((p) => p.id === id);
  if (!p) {
    toast("Selecciona un producto para agregar.");
    return;
  }
  const found = cart.find((i) => i.productoId === id);
  if ((found?.cantidad || 0) >= p.stockDisponible) {
    toast(
      "Ya agregaste todas las unidades disponibles de este producto.",
      true,
    );
    return;
  }
  if (found) found.cantidad++;
  else cart.push({ productoId: id, cantidad: 1 });
  renderCart();
}

async function submitMutation(form, type) {
  if (submitting) return;
  const data = Object.fromEntries(new FormData(form));
  let path, method, body, success;
  try {
    if (type === "product") {
      body = {
        nombre: data.nombre.trim(),
        codigo: data.codigo.trim(),
        precio: Number(data.precio),
        stockDisponible: Number(data.stockDisponible),
        stockMinimo: Number(data.stockMinimo),
      };
      if (!body.nombre || !body.codigo)
        throw new Error("El nombre y el código no pueden quedar vacíos.");
      path = "/productos" + (dialogData ? "/" + dialogData.id : "");
      method = dialogData ? "PUT" : "POST";
      success = dialogData ? "Producto actualizado." : "Producto creado.";
    }
    if (type === "client") {
      body = { nombre: data.nombre.trim(), email: data.email.trim() };
      if (!body.nombre) throw new Error("El nombre no puede quedar vacío.");
      path = "/clientes" + (dialogData ? "/" + dialogData.id : "");
      method = dialogData ? "PUT" : "POST";
      success = dialogData ? "Cliente actualizado." : "Cliente creado.";
    }
    if (type === "delete") {
      path = `/${dialogData.kind === "product" ? "productos" : "clientes"}/${dialogData.item.id}`;
      method = "DELETE";
      success =
        dialogData.kind === "product"
          ? "Producto desactivado. Su historial se conserva."
          : "Cliente desactivado. Su historial se conserva.";
    }
    if (type === "order") {
      if (!cart.length)
        throw new Error("Agrega al menos un producto al pedido.");
      if (cart.some((i) => !Number.isInteger(i.cantidad) || i.cantidad < 1))
        throw new Error(
          "Todas las cantidades deben ser números enteros mayores que cero.",
        );
      body = {
        clienteId: Number(data.clienteId),
        items: cart.map((i) => ({ ...i })),
      };
      path = "/pedidos";
      method = "POST";
      success = "Pedido creado. El inventario se actualizó correctamente.";
    }
    setSubmitting(true);
    const result = await api(path, { method, body });
    setSubmitting(false);
    closeModal();
    toast(success);
    await loadData({ quiet: true });
    if (type === "order") await orderDetail(result.id);
  } catch (err) {
    setSubmitting(false);
    showFormError(err);
  }
}

async function filterSubmit(form, type) {
  const data = Object.fromEntries(new FormData(form));
  try {
    if (type === "product-filter") {
      Object.assign(filters.inventario, {
        nombre: data.nombre.trim(),
        codigo: data.codigo.trim(),
        page: 1,
      });
      productResults = await api(
        `/productos?${query({ nombre: filters.inventario.nombre, codigo: filters.inventario.codigo })}`,
      );
    }
    if (type === "client-filter") {
      Object.assign(filters.clientes, {
        nombre: data.nombre.trim(),
        email: data.email.trim(),
        page: 1,
      });
      clientResults = await api(
        `/clientes?${query({ nombre: filters.clientes.nombre, email: filters.clientes.email })}`,
      );
    }
    if (type === "order-filter") {
      const q = periodQuery(data.desde, data.hasta);
      Object.assign(filters.pedidos, {
        desde: data.desde,
        hasta: data.hasta,
        page: 1,
      });
      orderResults = await api(`/pedidos?${q}`);
    }
    if (type === "report-filter") {
      periodQuery(data.desde, data.hasta);
      Object.assign(filters.reportes, data);
      state.reportKey = "";
      state.reportError = "";
    }
    render();
  } catch (err) {
    toast(err.message, true);
  }
}

function exportReport(format) {
  if (state.reportLoading || !state.report || state.reportError) {
    toast("Espera a que termine de generarse el reporte.", true);
    return;
  }
  const r = state.report,
    type = route().report;
  let content;
  if (format === "json") content = JSON.stringify(r, null, 2);
  else if (type === "inventario")
    content = csv([
      [
        "ID",
        "Nombre",
        "Código",
        "Precio CLP",
        "Stock disponible",
        "Stock mínimo",
        "Estado",
      ],
      ...r.productos.map((p) => [
        p.id,
        p.nombre,
        p.codigo,
        p.precio,
        p.stockDisponible,
        p.stockMinimo,
        statusLabel(productStatus(p)),
      ]),
    ]);
  else
    content = csv([
      [
        "Pedido",
        "Fecha",
        "Cliente ID",
        "Cliente",
        "Estado",
        "Producto ID",
        "Código",
        "Producto",
        "Cantidad",
        "Precio unitario CLP",
        "Subtotal CLP",
        "Total pedido CLP",
      ],
      ...r.pedidos.flatMap((o) =>
        o.items.map((i) => [
          orderCode(o.id),
          o.fecha,
          o.clienteId,
          o.clienteNombre,
          o.estado,
          i.productoId,
          i.codigo,
          i.nombre,
          i.cantidad,
          i.precioUnitario,
          i.subtotal,
          o.total,
        ]),
      ),
    ]);
  downloadFile(
    `stockflow-${type}-${dateKey(new Date())}.${format}`,
    content,
    format === "json"
      ? "application/json;charset=utf-8"
      : "text/csv;charset=utf-8",
  );
  toast(`Reporte ${format.toUpperCase()} descargado.`);
}

document.addEventListener("click", async (event) => {
  const el = event.target.closest("[data-action]");
  if (!el || el.disabled) return;
  event.preventDefault();
  const a = el.dataset.action,
    id = Number(el.dataset.id);
  if (submitting && modal.contains(el)) return;
  switch (a) {
    case "refresh":
      await loadData();
      break;
    case "new-product":
      productForm();
      break;
    case "edit-product":
      try {
        productForm(await api(`/productos/${id}`));
      } catch (err) {
        toast(err.message, true);
      }
      break;
    case "product-detail":
      await productDetail(id);
      break;
    case "delete-product":
      deleteDialog("product", id);
      break;
    case "new-client":
      clientForm();
      break;
    case "edit-client":
      try {
        clientForm(await api(`/clientes/${id}`));
      } catch (err) {
        toast(err.message, true);
      }
      break;
    case "client-detail":
      await clientDetail(id);
      break;
    case "delete-client":
      deleteDialog("client", id);
      break;
    case "new-order":
      await newOrder();
      break;
    case "client-new-order":
      await newOrder(id);
      break;
    case "order-detail":
      await orderDetail(id);
      break;
    case "order-json":
      downloadFile(
        `stockflow-pedido-${dialogData.id}.json`,
        JSON.stringify(dialogData, null, 2),
        "application/json;charset=utf-8",
      );
      break;
    case "close-modal":
      closeModal();
      break;
    case "chart-days":
      state.days = Number(el.dataset.days);
      render();
      break;
    case "stock-tab":
      filters.inventario.tab = el.dataset.tab;
      filters.inventario.page = 1;
      render();
      break;
    case "show-low":
      filters.inventario.tab = "low";
      filters.inventario.page = 1;
      location.hash = "#/inventario";
      break;
    case "clear-products":
      Object.assign(filters.inventario, {
        nombre: "",
        codigo: "",
        tab: "all",
        page: 1,
      });
      productResults = state.products;
      render();
      break;
    case "clear-clients":
      Object.assign(filters.clientes, { nombre: "", email: "", page: 1 });
      clientResults = state.clients;
      render();
      break;
    case "clear-orders":
      Object.assign(filters.pedidos, {
        desde: "",
        hasta: "",
        cliente: "",
        page: 1,
      });
      orderResults = state.orders;
      render();
      break;
    case "clear-report":
      Object.assign(filters.reportes, { desde: "", hasta: "" });
      state.reportKey = "";
      state.reportError = "";
      render();
      break;
    case "retry-report":
      state.reportKey = "";
      state.reportError = "";
      render();
      break;
    case "page":
      filters[el.dataset.key].page = Number(el.dataset.page);
      render();
      break;
    case "cart-add":
      addCart();
      break;
    case "cart-remove":
      cart = cart.filter((i) => i.productoId !== id);
      renderCart();
      break;
    case "cart-plus":
    case "cart-minus": {
      const item = cart.find((i) => i.productoId === id);
      const p = state.products.find((p) => p.id === id);
      item.cantidad = Math.max(
        1,
        Math.min(
          p.stockDisponible,
          item.cantidad + (a === "cart-plus" ? 1 : -1),
        ),
      );
      renderCart();
      break;
    }
    case "export-json":
      exportReport("json");
      break;
    case "export-csv":
      exportReport("csv");
      break;
  }
});
document.addEventListener("submit", (event) => {
  const form = event.target;
  if (!form.dataset.form) return;
  event.preventDefault();
  const type = form.dataset.form;
  if (["product", "client", "delete", "order"].includes(type))
    submitMutation(form, type);
  else filterSubmit(form, type);
});
document.addEventListener("change", (event) => {
  const el = event.target;
  if (el.dataset.sort) {
    filters[el.dataset.sort].sort = el.value;
    filters[el.dataset.sort].page = 1;
    render();
  }
  if (el.hasAttribute("data-order-client")) {
    filters.pedidos.cliente = el.value;
    filters.pedidos.page = 1;
    render();
  }
  if (el.dataset.quantity) {
    const item = cart.find((i) => i.productoId === Number(el.dataset.quantity));
    const p = state.products.find((p) => p.id === item.productoId);
    const n = Number(el.value);
    if (!Number.isInteger(n) || n < 1 || n > p.stockDisponible) {
      el.value = item.cantidad;
      toast(
        `La cantidad debe ser un entero entre 1 y ${p.stockDisponible}.`,
        true,
      );
      return;
    }
    item.cantidad = n;
    renderCart();
  }
});
$("#theme-toggle").addEventListener("click", () => {
  document.documentElement.dataset.theme =
    document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  try {
    localStorage.setItem(
      "stockflow-theme",
      document.documentElement.dataset.theme,
    );
  } catch {}
  updateTheme();
});
$("#refresh-button").addEventListener("click", () => loadData());
$("#menu-toggle").innerHTML = icon("menu", 20);
$("#menu-toggle").addEventListener("click", () => {
  const open = $("#sidebar").classList.toggle("open");
  document.body.classList.toggle("menu-open", open);
  $("#sidebar-overlay").hidden = !open;
  $("#menu-toggle").setAttribute("aria-expanded", String(open));
});
$("#sidebar-overlay").addEventListener("click", closeMenu);
matchMedia("(max-width:700px)").addEventListener("change", closeMenu);
$(".skip-link").addEventListener("click", (event) => {
  event.preventDefault();
  main.focus();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeMenu();
});
modal.addEventListener("cancel", (event) => {
  if (submitting) {
    event.preventDefault();
    return;
  }
  dialogKind = "";
  dialogData = null;
  cart = [];
});
window.addEventListener("hashchange", () => {
  closeMenu();
  reportSequence++;
  state.reportLoading = false;
  state.reportError = "";
  render();
  main.focus();
});
$("#refresh-button").innerHTML = icon("refresh", 17);
$("#today").textContent = new Intl.DateTimeFormat("es-CL", {
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date());
updateTheme();
navigation();
loadData();
