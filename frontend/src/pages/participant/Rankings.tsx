export default function Rankings() {
  return (
    <div className="container mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Rankings</h1>
      <div className="bg-white rounded-lg shadow">
        <table className="w-full">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-2 text-left">Rank</th>
              <th className="px-4 py-2 text-left">Team</th>
              <th className="px-4 py-2 text-left">Points</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="px-4 py-2 text-gray-600" colSpan={3}>
                No rankings available
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
