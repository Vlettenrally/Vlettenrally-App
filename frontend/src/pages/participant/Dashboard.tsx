export default function ParticipantDashboard() {
  return (
    <div className="container mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid gap-4 md:grid-cols-2">
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Your Team</h2>
          <p className="text-gray-600">Team info will appear here</p>
        </div>
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="font-semibold mb-2">Current Game</h2>
          <p className="text-gray-600">Game status will appear here</p>
        </div>
      </div>
    </div>
  );
}
