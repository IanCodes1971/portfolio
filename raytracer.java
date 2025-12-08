import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple Java ray tracer (single-file).
 * Save as RayTracer.java, then:
 *   javac RayTracer.java
 *   java RayTracer
 * Output: render.png
 */
public class raytracer {
    public static void main(String[] args) throws Exception {
        final int width = 800;
        final int height = 500;
        final int samplesPerPixel = 50; // anti-aliasing
        final int maxDepth = 5; // recursion depth for reflections

        Camera cam = new Camera(new Vec3(0, 1, 5), new Vec3(0, 0.5, 0), new Vec3(0, 1, 0), 60, (double) width / height);

        List<Hittable> world = new ArrayList<>();
        // ground
        world.add(new Sphere(new Vec3(0, -1000.5, 0), 1000, new MaterialDiffuse(new Vec3(0.8, 0.8, 0.0))));
        // spheres
        world.add(new Sphere(new Vec3(0, 0.5, 0), 0.5, new MaterialDiffuse(new Vec3(0.1, 0.2, 0.5))));
        world.add(new Sphere(new Vec3(-1.2, 0.5, -0.5), 0.5, new MaterialMetal(new Vec3(0.8, 0.6, 0.2), 0.0)));
        world.add(new Sphere(new Vec3(1.2, 0.5, -0.5), 0.5, new MaterialMetal(new Vec3(0.8, 0.8, 0.8), 0.3)));
        // translucent (optional) -- if you'd like dielectric, you can swap in MaterialDielectric below
        // world.add(new Sphere(new Vec3(0, 0.5, -1.0), 0.5, new MaterialDielectric(1.5)));

        // single point light
        Vec3 lightPos = new Vec3(5, 10, 5);
        Vec3 lightColor = new Vec3(1.0, 1.0, 1.0);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random();

        for (int j = 0; j < height; j++) {
            System.out.printf("Scanline %d/%d\r", j+1, height);
            for (int i = 0; i < width; i++) {
                Vec3 col = new Vec3(0, 0, 0);
                for (int s = 0; s < samplesPerPixel; s++) {
                    double u = (i + rng.nextDouble()) / (width - 1);
                    double v = (height - 1 - j + rng.nextDouble()) / (height - 1); // flip Y
                    Ray r = cam.getRay(u, v);
                    col = col.add(rayColor(r, world, lightPos, lightColor, maxDepth));
                }
                // average and gamma-correct (gamma=2)
                col = col.scale(1.0 / samplesPerPixel);
                col = new Vec3(Math.sqrt(col.x), Math.sqrt(col.y), Math.sqrt(col.z));

                int ir = clampAndToInt(col.x);
                int ig = clampAndToInt(col.y);
                int ib = clampAndToInt(col.z);
                int rgb = (ir << 16) | (ig << 8) | ib;
                img.setRGB(i, j, rgb);
            }
        }

        ImageIO.write(img, "png", new File("render.png"));
        System.out.println("\nDone. Wrote render.png");
    }

    static int clampAndToInt(double x) {
        x = Math.max(0.0, Math.min(0.999, x));
        return (int) (x * 256);
    }

    // Compute color by tracing the ray; uses simple direct lighting + shadows + reflections
    static Vec3 rayColor(Ray r, List<Hittable> world, Vec3 lightPos, Vec3 lightColor, int depth) {
        if (depth <= 0) return new Vec3(0, 0, 0);

        HitRecord rec = hitWorld(world, r, 1e-4, Double.POSITIVE_INFINITY);
        if (rec != null) {
            // Ambient term
            Vec3 ambient = rec.mat.albedo().scale(0.05);

            // Direct illumination from single point light (Lambertian)
            Vec3 L = lightPos.subtract(rec.p).normalized();
            Ray shadowRay = new Ray(rec.p, L);
            HitRecord shadowHit = hitWorld(world, shadowRay, 1e-4, lightPos.subtract(rec.p).length() - 1e-4);
            boolean inShadow = shadowHit != null;

            Vec3 direct = new Vec3(0,0,0);
            if (!inShadow) {
                double nDotL = Math.max(0.0, rec.normal.dot(L));
                double distance2 = lightPos.subtract(rec.p).lengthSquared();
                double attenuation = 1.0 / Math.max(1.0, distance2 * 0.01); // soft attenuation factor
                direct = rec.mat.albedo().multiply(lightColor).scale(nDotL * attenuation);
            }

            Vec3 reflected = new Vec3(0,0,0);
            if (rec.mat instanceof MaterialMetal) {
                // reflection for metal
                Vec3 reflectDir = reflect(r.dir.normalized(), rec.normal);
                Ray reflectedRay = new Ray(rec.p, reflectDir.add(randomInUnitSphere().scale(((MaterialMetal)rec.mat).fuzz)).normalized());
                reflected = rayColor(reflectedRay, world, lightPos, lightColor, depth - 1).scale( ((MaterialMetal)rec.mat).reflectanceFactor() );
            }
            // combine
            return ambient.add(direct).add(reflected).clamp(0.0, 1.0);
        }

        // Background gradient
        Vec3 unitDir = r.dir.normalized();
        double t = 0.5 * (unitDir.y + 1.0);
        return new Vec3(1.0, 1.0, 1.0).scale(1.0 - t).add(new Vec3(0.5, 0.7, 1.0).scale(t));
    }

    static Ray randomInUnitSphereRay(Random rng) {
        Vec3 p;
        do {
            p = new Vec3(rng.nextDouble()*2-1, rng.nextDouble()*2-1, rng.nextDouble()*2-1);
        } while (p.lengthSquared() >= 1.0);
        return new Ray(new Vec3(0,0,0), p);
    }

    static Vec3 randomInUnitSphere() {
        Random r = new Random();
        Vec3 p;
        do {
            p = new Vec3(r.nextDouble()*2-1, r.nextDouble()*2-1, r.nextDouble()*2-1);
        } while (p.lengthSquared() >= 1.0);
        return p;
    }

    static Vec3 reflect(Vec3 v, Vec3 n) {
        return v.subtract(n.scale(2 * v.dot(n)));
    }

    static HitRecord hitWorld(List<Hittable> world, Ray r, double tMin, double tMax) {
        HitRecord hitAnything = null;
        double closestSoFar = tMax;
        for (Hittable h : world) {
            HitRecord hr = h.hit(r, tMin, closestSoFar);
            if (hr != null) {
                closestSoFar = hr.t;
                hitAnything = hr;
            }
        }
        return hitAnything;
    }

    /* -----------------------------
       Supporting classes below
       ----------------------------- */

    static class Vec3 {
        double x, y, z;
        Vec3(double a, double b, double c) { x = a; y = b; z = c; }
        Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
        Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
        Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }
        Vec3 multiply(Vec3 o) { return new Vec3(x * o.x, y * o.y, z * o.z); }
        Vec3 clamp(double a, double b) {
            return new Vec3(Math.max(a, Math.min(b, x)), Math.max(a, Math.min(b, y)), Math.max(a, Math.min(b, z)));
        }
        double dot(Vec3 o) { return x*o.x + y*o.y + z*o.z; }
        Vec3 cross(Vec3 o) { return new Vec3(y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x); }
        double length() { return Math.sqrt(x*x + y*y + z*z); }
        double lengthSquared() { return x*x + y*y + z*z; }
        Vec3 normalized() { double L = length(); return (L==0) ? new Vec3(0,0,0) : scale(1.0/L); }
    }

    static class Ray {
        Vec3 origin, dir;
        Ray(Vec3 o, Vec3 d) { origin = o; dir = d; }
        Vec3 at(double t) { return origin.add(dir.scale(t)); }
    }

    interface Hittable {
        HitRecord hit(Ray r, double tMin, double tMax);
    }

    static class HitRecord {
        Vec3 p;
        Vec3 normal;
        double t;
        Material mat;
        HitRecord(Vec3 p, Vec3 normal, double t, Material mat) { this.p = p; this.normal = normal; this.t = t; this.mat = mat; }
    }

    static class Sphere implements Hittable {
        Vec3 center;
        double radius;
        Material mat;
        Sphere(Vec3 c, double r, Material m) { center = c; radius = r; mat = m; }

        @Override
        public HitRecord hit(Ray r, double tMin, double tMax) {
            Vec3 oc = r.origin.subtract(center);
            double a = r.dir.dot(r.dir);
            double half_b = oc.dot(r.dir);
            double c = oc.dot(oc) - radius*radius;
            double discriminant = half_b*half_b - a*c;
            if (discriminant < 0) return null;
            double sqrtd = Math.sqrt(discriminant);

            // find nearest root in acceptable range
            double root = (-half_b - sqrtd) / a;
            if (root < tMin || root > tMax) {
                root = (-half_b + sqrtd) / a;
                if (root < tMin || root > tMax) return null;
            }

            Vec3 p = r.at(root);
            Vec3 outwardNormal = p.subtract(center).scale(1.0 / radius);
            return new HitRecord(p, outwardNormal.normalized(), root, mat);
        }
    }

    /* Materials */
    static abstract class Material {
        abstract Vec3 albedo();
    }

    static class MaterialDiffuse extends Material {
        Vec3 color;
        MaterialDiffuse(Vec3 c) { color = c; }
        @Override Vec3 albedo() { return color; }
    }

    static class MaterialMetal extends Material {
        Vec3 color;
        double fuzz;
        MaterialMetal(Vec3 c, double f) { color = c; fuzz = Math.max(0.0, Math.min(f, 1.0)); }
        @Override Vec3 albedo() { return color; }
        double reflectanceFactor() { return (color.x + color.y + color.z) / 3.0; }
    }

    // (Optional) dielectric placeholder if you want to implement glass later
    static class MaterialDielectric extends Material {
        double ir;
        MaterialDielectric(double indexOfRefraction) { ir = indexOfRefraction; }
        @Override Vec3 albedo() { return new Vec3(1,1,1); }
    }

    /* Camera (pinhole) */
    static class Camera {
        Vec3 origin;
        Vec3 lowerLeftCorner;
        Vec3 horizontal;
        Vec3 vertical;
        Vec3 u, v, w;
        double lensRadius = 0.0;

        Camera(Vec3 lookFrom, Vec3 lookAt, Vec3 vup, double vfovDeg, double aspect) {
            origin = lookFrom;
            double theta = Math.toRadians(vfovDeg);
            double h = Math.tan(theta/2);
            double viewportHeight = 2.0 * h;
            double viewportWidth = aspect * viewportHeight;

            w = lookFrom.subtract(lookAt).normalized();
            u = vup.cross(w).normalized();
            v = w.cross(u);

            lowerLeftCorner = origin.subtract(u.scale(viewportWidth/2)).subtract(v.scale(viewportHeight/2)).subtract(w);
            horizontal = u.scale(viewportWidth);
            vertical = v.scale(viewportHeight);
        }

        Ray getRay(double s, double t) {
            Vec3 dir = lowerLeftCorner.add(horizontal.scale(s)).add(vertical.scale(t)).subtract(origin);
            return new Ray(origin, dir.normalized());
        }
    }
}
