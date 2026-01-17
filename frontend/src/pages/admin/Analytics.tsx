export default function Analytics() {
  return (
    <div className="container mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Analytics</h1>
      <div className="grid gap-4 md:grid-cols-2">
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Team Activity</h2>
          <p className="text-gray-600">No data available</p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Message Delivery</h2>
          <p className="text-gray-600">No data available</p>
        </div>
      </div>
    </div>
  );
}
