export default function AdminDashboard() {
  return (
    <div className="container mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Admin Dashboard</h1>
      <div className="grid gap-4 md:grid-cols-3">
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Active Games</h2>
          <p className="text-3xl font-bold">0</p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Checked-in Teams</h2>
          <p className="text-3xl font-bold">0</p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Messages Sent</h2>
          <p className="text-3xl font-bold">0</p>
        </div>
      </div>
    </div>
  );
}
