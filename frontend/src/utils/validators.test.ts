import { describe, expect, it } from 'vitest';
import {
  isRequired,
  isPhoneNumber,
  isEmail,
  minLength,
  maxLength,
  validateEmergencyContact,
  validateParticipantNames,
  validateScoutingGroup,
} from './validators';

describe('isPhoneNumber (Dutch format)', () => {
  it('accepts valid mobile and landline numbers, with or without formatting', () => {
    expect(isPhoneNumber('0612345678')).toBe(true);
    expect(isPhoneNumber('06-12345678')).toBe(true);
    expect(isPhoneNumber('06 12 34 56 78')).toBe(true);
    expect(isPhoneNumber('+31612345678')).toBe(true);
    expect(isPhoneNumber('0201234567')).toBe(true); // landline
  });

  it('rejects malformed numbers', () => {
    expect(isPhoneNumber('0012345678')).toBe(false); // second digit must be 1-9
    expect(isPhoneNumber('061234567')).toBe(false); // too short
    expect(isPhoneNumber('06123456789')).toBe(false); // too long
    expect(isPhoneNumber('+32612345678')).toBe(false); // wrong country code
    expect(isPhoneNumber('abcdefghij')).toBe(false);
    expect(isPhoneNumber('')).toBe(false);
  });
});

describe('isEmail', () => {
  it('accepts well-formed addresses and rejects bad ones', () => {
    expect(isEmail('team@example.com')).toBe(true);
    expect(isEmail('a.b@sub.example.co.uk')).toBe(true);
    expect(isEmail('no-at-sign')).toBe(false);
    expect(isEmail('missing@domain')).toBe(false);
    expect(isEmail('spaces in@email.com')).toBe(false);
  });
});

describe('isRequired / minLength / maxLength', () => {
  it('isRequired trims whitespace', () => {
    expect(isRequired('x')).toBe(true);
    expect(isRequired('   ')).toBe(false);
    expect(isRequired('')).toBe(false);
  });

  it('minLength / maxLength are inclusive', () => {
    expect(minLength('ab', 2)).toBe(true);
    expect(minLength('a', 2)).toBe(false);
    expect(maxLength('ab', 2)).toBe(true);
    expect(maxLength('abc', 2)).toBe(false);
  });
});

describe('validateParticipantNames', () => {
  it('requires at least one name', () => {
    expect(validateParticipantNames([])).toBe('Voeg minimaal één deelnemer toe');
  });

  it('rejects blank or whitespace-only entries', () => {
    expect(validateParticipantNames(['Alice', '   '])).toBe('Alle namen moeten ingevuld zijn');
    expect(validateParticipantNames(['Alice', ''])).toBe('Alle namen moeten ingevuld zijn');
  });

  it('returns null when all names are filled', () => {
    expect(validateParticipantNames(['Alice', 'Bob'])).toBeNull();
  });
});

describe('validateEmergencyContact', () => {
  it('requires a value and a valid phone number', () => {
    expect(validateEmergencyContact('')).toBe('Noodcontact is verplicht');
    expect(validateEmergencyContact('not-a-phone')).toBe('Ongeldig telefoonnummer');
    expect(validateEmergencyContact('0612345678')).toBeNull();
  });
});

describe('validateScoutingGroup', () => {
  it('requires a non-empty value of at least 2 characters', () => {
    expect(validateScoutingGroup('')).toBe('Scoutinggroep is verplicht');
    expect(validateScoutingGroup('a')).toBe('Scoutinggroep moet minimaal 2 karakters zijn');
    expect(validateScoutingGroup('Vikingen')).toBeNull();
  });
});
