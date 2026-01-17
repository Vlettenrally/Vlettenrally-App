import { useState } from 'react';

export default function CheckIn() {
  const [boatNumber, setBoatNumber] = useState('');
  const [emergencyContact, setEmergencyContact] = useState('');
  const [participantNames, setParticipantNames] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // TODO: Submit check-in data
    console.log({ boatNumber, emergencyContact, participantNames });
  };

  return (
    <div className="container mx-auto p-4 max-w-md">
      <h1 className="text-2xl font-bold mb-6">Team Check-in</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Boat Number</label>
          <input
            type="text"
            value={boatNumber}
            onChange={(e) => setBoatNumber(e.target.value)}
            className="w-full border rounded px-3 py-2"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Emergency Contact</label>
          <input
            type="text"
            value={emergencyContact}
            onChange={(e) => setEmergencyContact(e.target.value)}
            className="w-full border rounded px-3 py-2"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Participant Names</label>
          <textarea
            value={participantNames}
            onChange={(e) => setParticipantNames(e.target.value)}
            className="w-full border rounded px-3 py-2"
            rows={3}
            placeholder="One name per line"
          />
        </div>
        <button
          type="submit"
          className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700"
        >
          Check In
        </button>
      </form>
    </div>
  );
}
