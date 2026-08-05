/**
 * Hand a file fetched from the API to the browser as a download.
 *
 * The exports need a bearer token, so the browser cannot simply navigate to the URL —
 * the body is fetched and saved from here instead. It stays a Blob all the way from the
 * response so the bytes reach disk exactly as the server wrote them, including the UTF-8
 * BOM the ranking export relies on for Excel.
 */
export function downloadFile(file: Blob, filename: string): void {
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
