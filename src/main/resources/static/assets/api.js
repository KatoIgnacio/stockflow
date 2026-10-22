export class ApiError extends Error {
  constructor(message, status = 0, details = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}
export async function api(path, { method = "GET", body, signal } = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20000);
  const abort = () => controller.abort();
  signal?.addEventListener("abort", abort, { once: true });
  try {
    const response = await fetch(`/api${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await response.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      throw new ApiError(
        "La respuesta del servidor no se pudo interpretar.",
        response.status,
      );
    }
    if (!response.ok)
      throw new ApiError(
        data?.mensaje || "No pudimos completar la operación.",
        response.status,
        data?.detalles || [],
      );
    return data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error.name === "AbortError")
      throw new ApiError("La solicitud tardó demasiado. Intenta nuevamente.");
    throw new ApiError(
      "No pudimos conectar con StockFlow. Comprueba que la aplicación esté iniciada.",
    );
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", abort);
  }
}
