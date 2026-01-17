export default function Dev() {
  return (
    <div className="container mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Developer Tools</h1>
      <div className="space-y-4">
        <div className="bg-yellow-50 border border-yellow-200 rounded p-4">
          <p className="text-yellow-800">
            This page is for development and testing purposes only.
          </p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">WebSocket Status</h2>
          <p className="text-gray-600">Not connected</p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">API Status</h2>
          <p className="text-gray-600">Unknown</p>
        </div>
      </div>
    </div>
  );
}
