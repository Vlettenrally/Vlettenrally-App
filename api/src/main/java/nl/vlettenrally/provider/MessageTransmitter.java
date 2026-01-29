package nl.vlettenrally.provider;

import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Interface for message transmission implementations.
 * Each implementation handles a specific transmission channel (e.g., Firebase Push, SMS).
 */
public interface MessageTransmitter {

    /**
     * Get the transmission channel this transmitter handles.
     */
    TransmissionChannel getChannel();

    /**
     * Check if this transmitter can send messages to the given team.
     * For example, a Firebase transmitter requires an FCM token, 
     * an SMS transmitter requires a phone number.
     *
     * @param team the target team
     * @return true if this transmitter can send to the team
     */
    boolean supports(Team team);

    /**
     * Transmit a message to a team.
     *
     * @param message the message to transmit
     * @param team the target team
     * @return the result of the transmission attempt
     */
    TransmissionResult transmit(Message message, Team team);
}
