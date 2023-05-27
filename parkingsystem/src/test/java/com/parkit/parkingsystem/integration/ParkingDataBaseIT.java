package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;
    private static FareCalculatorService fareCalculatorService;


    @Mock
    private static InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() throws Exception{
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
        fareCalculatorService = new FareCalculatorService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown(){
    }

    @Test
    public void testParkingACar(){
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        final Ticket testParkingACarTicket = ticketDAO.getTicket("ABCDEF");
        final ParkingSpot testSpotParkingACar = testParkingACarTicket.getParkingSpot();

        //check that a ticket is actually saved in DB and Parking table is updated with availability

        Assertions.assertEquals(1, testParkingACarTicket.getId());
        Assertions.assertFalse(testSpotParkingACar.isAvailable());
    }

    @Test
    public void testParkingLotExit() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Ticket ticket = new Ticket();
        final int parkingSpotNumber = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        final ParkingSpot parkingSpot = new ParkingSpot(parkingSpotNumber, ParkingType.CAR, false);
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABCDEF");
        ticket.setInTime(Date.from(LocalDateTime.now().minusHours(1).atZone(ZoneId.systemDefault()).toInstant()));

        ticketDAO.saveTicket(ticket);

        parkingService.processExitingVehicle();
        ticket = ticketDAO.getTicket("ABCDEF");

        // check that the fare generated and out time are populated correctly in the database
        Assertions.assertEquals(Fare.CAR_RATE_PER_HOUR, ticket.getPrice(), 0.01);
        Assertions.assertNotNull(ticket.getOutTime());
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        
        //Create a ticket in DB with a car who already was in
        Ticket carAlreadyExisting = new Ticket();
        final int parkingSpotNumber = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        final ParkingSpot parkingSpot = new ParkingSpot(parkingSpotNumber, ParkingType.CAR, false);
        carAlreadyExisting.setParkingSpot(parkingSpot);
        carAlreadyExisting.setVehicleRegNumber("ABCDEF");
        carAlreadyExisting.setInTime(Date.from(LocalDateTime.now().minusHours(8).atZone(ZoneId.systemDefault()).toInstant()));
        carAlreadyExisting.setOutTime(Date.from(LocalDateTime.now().minusHours(4).atZone(ZoneId.systemDefault()).toInstant()));
        fareCalculatorService.calculateFare(carAlreadyExisting);
        ticketDAO.getNbTicket("ABCDEF");
        ticketDAO.saveTicket(carAlreadyExisting);

        parkingService.processIncomingVehicle();
        Thread.sleep(500);
        parkingService.processExitingVehicle();

        Ticket carReturning = ticketDAO.getTicket("ABCDEF");
        carReturning.setOutTime(Date.from(LocalDateTime.now().plusHours(1).atZone(ZoneId.systemDefault()).toInstant()));
        fareCalculatorService.calculateFare(carReturning, true);
        ticketDAO.updateTicket(carReturning);

        Assertions.assertEquals(Fare.CAR_RATE_PER_HOUR * 4, carAlreadyExisting.getPrice(), 0.001);
        Assertions.assertEquals(Fare.CAR_RATE_PER_HOUR * 0.95, carReturning.getPrice(), 0.001);
    }    
}
