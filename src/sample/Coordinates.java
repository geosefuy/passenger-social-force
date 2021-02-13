package sample;

import java.util.Objects;

// Represents a pair of 2D Cartesian coordinates
public class Coordinates {
    private double x;
    private double y;

    public Coordinates(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // Get the central coordinates of the given patch
    public static Coordinates patchCenterCoordinates(Patch patch) {
        // Retrieve the row and column positions of the patch
        double column = patch.getMatrixPosition().getColumn();
        double row = patch.getMatrixPosition().getRow();

        // Set the centered x and y coordinates
        double centeredX = column + 0.5;
        double centeredY = row + 0.5;

        return new Coordinates(centeredX, centeredY);
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    // Compute the distance between this coordinates and some other coordinates
    public static double distance(Coordinates sourceCoordinates, Coordinates coordinates) {
        double x = coordinates.getX();
        double y = coordinates.getY();

        return Math.sqrt(
                Math.pow(x - sourceCoordinates.getX(), 2) + Math.pow(y - sourceCoordinates.getY(), 2)
        );
    }


    // Retrieve the heading (in radians) when it faces towards a given position
    public static double headingTowards(Coordinates sourceCoordinates, Coordinates coordinates) {
        double x = coordinates.getX();
        double y = coordinates.getY();

        // Get the differences in the x and y values of the two positions
        double dx = x - sourceCoordinates.getX();
        double dy = y - sourceCoordinates.getY();

        // The length of the adjacent side is the difference between the x values of the target position and the
        // passenger
        double adjacentLength = dx;

        // The length of the hypotenuse is the distance between this passenger and the given position
        double hypotenuseLength = distance(sourceCoordinates, coordinates);

        // The included angle between the adjacent and the hypotenuse is given by the arccosine of the ratio of the
        // length of the adjacent and the length of the hypotenuse
        double angle = Math.acos(adjacentLength / hypotenuseLength);

        // If the difference between the y values of the target position and this passenger is positive (meaning the
        // target patch is below the passenger), get the supplement of the angle
        if (dy > 0) {
            angle = 2.0 * Math.PI - angle;
        }

        return angle;
    }

    // See if the given coordinate is within the passenger's field of view
    public static boolean isWithinFieldOfView(Coordinates sourceCoordinates,
                                              Coordinates coordinates,
                                              double heading,
                                              double maximumHeadingChange) {
        // A coordinate is within a field of view if the heading change required to face that coordinate is within the
        // specified maximum heading change
        double headingTowardsCoordinate = headingTowards(sourceCoordinates, coordinates);

        // Compute the absolute difference between the two headings
        double headingDifference = Coordinates.headingDifference(headingTowardsCoordinate, heading);

        // If the heading difference is within the specified parameter, return true
        // If not, the passenger is outside this passenger's field of view, so return false
        return headingDifference <= maximumHeadingChange;
    }

    // Compute the angular difference between two headings
    public static double headingDifference(double heading1, double heading2) {
        double headingDifference = Math.abs(heading1 - heading2);

        if (headingDifference > Math.toRadians(180)) {
            headingDifference = Math.toRadians(360) - headingDifference;
        }

        return headingDifference;
    }

    // Compute for the angular mean of two headings
    public static double meanHeading(double heading1, double heading2) {
        return Math.atan2(
                (Math.sin(heading1) + Math.sin(heading2)) / 2.0,
                (Math.cos(heading1) + Math.cos(heading2)) / 2.0
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinates that = (Coordinates) o;
        return Double.compare(that.x, x) == 0 &&
                Double.compare(that.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
