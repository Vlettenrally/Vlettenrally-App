export default function Games() {
  return (
    <div className="container mx-auto p-4">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Games</h1>
        <button className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          Create Game
        </button>
      </div>
      <div className="bg-white rounded-lg shadow">
        <p className="p-4 text-gray-600">No games created yet</p>
      </div>
    </div>
  );
}
