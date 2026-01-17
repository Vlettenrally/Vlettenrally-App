/**
 * Form Validators
 */

export function isRequired(value: string): boolean {
  return value.trim().length > 0;
}

export function isBoatNumber(value: string): boolean {
  return /^\d+$/.test(value.trim());
}

export function isPhoneNumber(value: string): boolean {
  // Dutch phone number format (flexible)
  return /^(\+31|0)[1-9]\d{8}$/.test(value.replace(/[\s-]/g, ''));
}

export function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function minLength(value: string, min: number): boolean {
  return value.length >= min;
}

export function maxLength(value: string, max: number): boolean {
  return value.length <= max;
}

export function validateBoatNumber(value: string): string | null {
  if (!isRequired(value)) return 'Bootnummer is verplicht';
  if (!isBoatNumber(value)) return 'Bootnummer moet een getal zijn';
  return null;
}

export function validateEmergencyContact(value: string): string | null {
  if (!isRequired(value)) return 'Noodcontact is verplicht';
  if (!isPhoneNumber(value)) return 'Ongeldig telefoonnummer';
  return null;
}

export function validateParticipantNames(names: string[]): string | null {
  if (names.length === 0) return 'Voeg minimaal één deelnemer toe';
  if (names.some((name) => !isRequired(name))) {
    return 'Alle namen moeten ingevuld zijn';
  }
  return null;
}

export function validateScoutingGroup(value: string): string | null {
  if (!isRequired(value)) return 'Scoutinggroep is verplicht';
  if (!minLength(value, 2)) return 'Scoutinggroep moet minimaal 2 karakters zijn';
  return null;
}
